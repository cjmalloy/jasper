package jasper.component;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.png.PngMetadataReader;
import com.drew.imaging.tiff.TiffMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class Images {
	private static final Logger logger = LoggerFactory.getLogger(Images.class);
	private static final int THUMBNAIL_SIZE = 192;

	@Autowired
	Props props;

	/**
	 * Result of attempting to generate a thumbnail.
	 */
	public sealed interface Result permits Thumbnail, AlreadySmall, Unsupported, Failed {}
	/**
	 * Generated PNG thumbnail.
	 */
	public record Thumbnail(byte[] data) implements Result {}
	/**
	 * The image is small enough to be its own thumbnail.
	 */
	public record AlreadySmall() implements Result {}
	/**
	 * The input is not an image we can (or are willing to) thumbnail.
	 */
	public record Unsupported() implements Result {}
	/**
	 * An error occurred generating the thumbnail.
	 */
	public record Failed() implements Result {}

	private static class UnsupportedImageException extends Exception {
		UnsupportedImageException(String message) {
			super(message, null, false, false);
		}
	}

	@Timed(value = "jasper.images")
	public Result thumbnail(InputStream image) {
		try (image) {
			var data = image.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, (long) props.getImageMaxBytes() + 1));
			if (data.length > props.getImageMaxBytes()) {
				logger.debug("Image too large for thumbnail (over {} bytes)", props.getImageMaxBytes());
				return new Unsupported();
			}
			return thumbnail(data);
		} catch (IOException e) {
			logger.debug("Error reading image", e);
			return new Failed();
		}
	}

	@Timed(value = "jasper.images")
	public Result thumbnail(byte[] imageData) {
		if (imageData.length > props.getImageMaxBytes()) {
			logger.debug("Image too large for thumbnail ({} bytes)", imageData.length);
			return new Unsupported();
		}
		try {
			var bi = decode(imageData);

			// Apply orientation if needed
			bi = rotateImageIfNeeded(bi, orientation(imageData));

			var bo = new ByteArrayOutputStream();
			var width = bi.getWidth();
			var height = bi.getHeight();

			if (width > THUMBNAIL_SIZE || height > THUMBNAIL_SIZE) {
				var ar = (double) width / height;
				if (width > height) {
					width = THUMBNAIL_SIZE;
					height = (int) Math.floor(THUMBNAIL_SIZE / ar);
				} else {
					height = THUMBNAIL_SIZE;
					width = (int) Math.floor(THUMBNAIL_SIZE * ar);
				}
				var scaled = bi.getScaledInstance(width, height, Image.SCALE_SMOOTH);
				ImageIO.write(buffer(scaled, width, height), "png", bo);
			} else {
				return new AlreadySmall();
			}

			return new Thumbnail(bo.toByteArray());
		} catch (UnsupportedImageException e) {
			logger.debug(e.getMessage());
			return new Unsupported();
		} catch (Exception | StackOverflowError e) {
			logger.debug("Error resizing thumbnail", e);
			return new Failed();
		} catch (OutOfMemoryError e) {
			logger.warn("Out of memory resizing thumbnail", e);
			return new Failed();
		}
	}

	private BufferedImage decode(byte[] imageData) throws IOException, UnsupportedImageException {
		ImageInputStream iis = null;
		ImageReader reader = null;
		try {
			iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData));
			if (iis == null) throw new UnsupportedImageException("Could not read image");
			var readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) throw new UnsupportedImageException("Could not read image");
			reader = readers.next();
			reader.setInput(iis, true, true);
			var width = reader.getWidth(0);
			var height = reader.getHeight(0);
			var pixels = (long) width * height;
			if (pixels > props.getImageMaxPixels()) {
				throw new UnsupportedImageException("Image too large for thumbnail (" + pixels + " pixels)");
			}
			// Subsample while decoding so the long edge stays at least 2x the thumbnail size
			var n = Math.max(1, Math.max(width, height) / (THUMBNAIL_SIZE * 2));
			var param = reader.getDefaultReadParam();
			param.setSourceSubsampling(n, n, 0, 0);
			return reader.read(0, param);
		} finally {
			if (reader != null) reader.dispose();
			if (iis != null) iis.close();
		}
	}

	/**
	 * Read the EXIF orientation, falling back to 1 (normal) on any parser failure.
	 */
	int orientation(byte[] imageData) {
		try {
			var metadata = readMetadata(imageData);
			if (metadata == null) return 1;
			var directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
		} catch (Exception | StackOverflowError | AssertionError e) {
			logger.debug("Error reading EXIF data", e);
		} catch (OutOfMemoryError e) {
			logger.warn("Out of memory reading EXIF data", e);
		}
		return 1;
	}

	/**
	 * Only parse metadata for image types we can thumbnail.
	 */
	Metadata readMetadata(byte[] imageData) throws Exception {
		var is = new BufferedInputStream(new ByteArrayInputStream(imageData));
		return switch (FileTypeDetector.detectFileType(is)) {
			case Jpeg -> JpegMetadataReader.readMetadata(is, List.of(new ExifReader()));
			case Png -> PngMetadataReader.readMetadata(is);
			case Tiff -> isBigTiff(imageData) ? null : TiffMetadataReader.readMetadata(is);
			default -> null;
		};
	}

	private static boolean isBigTiff(byte[] imageData) {
		return imageData.length >= 4 && (
			imageData[0] == 'I' && imageData[2] == 0x2B ||
			imageData[0] == 'M' && imageData[3] == 0x2B);
	}

	private BufferedImage rotateImageIfNeeded(BufferedImage image, int orientation) {
		return switch (orientation) {
			case 1 -> image; // Normal
			case 3 -> // 180 rotate
				rotate(image, Math.PI);
			case 6 -> // 90 CW
				rotate(image, Math.PI / 2);
			case 8 -> // 270 CW
				rotate(image, -Math.PI / 2);
			default -> image;
		};
	}

	private BufferedImage rotate(BufferedImage image, double angle) {
		var w = image.getWidth();
		var h = image.getHeight();

		// Quarter turns swap width and height
		var quarter = Math.abs(Math.sin(angle)) > 0.5;
		var rw = quarter ? h : w;
		var rh = quarter ? w : h;

		var type = image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : image.getType();
		var rotated = new BufferedImage(rw, rh, type);
		var g = rotated.createGraphics();

		g.translate(rw / 2.0, rh / 2.0);
		g.rotate(angle);
		g.translate(-w / 2.0, -h / 2.0);
		g.drawImage(image, 0, 0, null);
		g.dispose();

		return rotated;
	}

	public static BufferedImage buffer(Image image, int width, int height) {
		if (image instanceof BufferedImage) return (BufferedImage) image;
		var bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		var g = bi.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return bi;
	}
}
