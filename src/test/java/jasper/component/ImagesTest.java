package jasper.component;

import com.drew.metadata.Metadata;
import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

public class ImagesTest {

	Images images;

	@BeforeEach
	void init() {
		images = new Images();
		images.props = new Props();
	}

	BufferedImage quadrant() {
		// Red top-left quadrant, blue everywhere else
		var bi = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
		var g = bi.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, 400, 400);
		g.setColor(Color.RED);
		g.fillRect(0, 0, 200, 200);
		g.dispose();
		return bi;
	}

	byte[] jpeg(BufferedImage bi) throws IOException {
		var bo = new ByteArrayOutputStream();
		ImageIO.write(bi, "jpg", bo);
		return bo.toByteArray();
	}

	byte[] withExifOrientation(byte[] jpeg, int orientation) {
		var tiff = ByteBuffer.allocate(26);
		tiff.put(new byte[]{ 'M', 'M', 0, 0x2A });
		tiff.putInt(8); // IFD0 offset
		tiff.putShort((short) 1); // entry count
		tiff.putShort((short) 0x0112); // orientation tag
		tiff.putShort((short) 3); // SHORT
		tiff.putInt(1); // count
		tiff.putShort((short) orientation);
		tiff.putShort((short) 0);
		tiff.putInt(0); // next IFD
		var exif = new ByteArrayOutputStream();
		exif.writeBytes(new byte[]{ 'E', 'x', 'i', 'f', 0, 0 });
		exif.writeBytes(tiff.array());
		var len = exif.size() + 2;
		var out = new ByteArrayOutputStream();
		out.write(0xFF);
		out.write(0xD8);
		out.write(0xFF);
		out.write(0xE1);
		out.write(len >> 8);
		out.write(len & 0xFF);
		out.writeBytes(exif.toByteArray());
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	byte[] pngHeader(int width, int height) {
		var ihdr = ByteBuffer.allocate(17);
		ihdr.put(new byte[]{ 'I', 'H', 'D', 'R' });
		ihdr.putInt(width);
		ihdr.putInt(height);
		ihdr.put(new byte[]{ 8, 2, 0, 0, 0 });
		var crc = new CRC32();
		crc.update(ihdr.array());
		var out = ByteBuffer.allocate(8 + 4 + 17 + 4);
		out.put(new byte[]{ (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		out.putInt(13);
		out.put(ihdr.array());
		out.putInt((int) crc.getValue());
		return out.array();
	}

	byte[] thumbnail(Images.Result result) {
		assertThat(result).isInstanceOf(Images.Thumbnail.class);
		return ((Images.Thumbnail) result).data();
	}

	boolean isRed(int rgb) {
		var c = new Color(rgb, true);
		return c.getRed() > 200 && c.getBlue() < 60;
	}

	boolean isBlue(int rgb) {
		var c = new Color(rgb, true);
		return c.getBlue() > 200 && c.getRed() < 60;
	}

	@Test
	void testThumbnail() throws IOException {
		var result = thumbnail(images.thumbnail(jpeg(quadrant())));

		var thumb = ImageIO.read(new ByteArrayInputStream(result));
		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isRed(thumb.getRGB(10, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(181, 10))).isTrue();
	}

	@Test
	void testExifOrientationRotates() throws IOException {
		var data = withExifOrientation(jpeg(quadrant()), 6);
		assertThat(images.orientation(data)).isEqualTo(6);

		var result = thumbnail(images.thumbnail(data));

		var thumb = ImageIO.read(new ByteArrayInputStream(result));
		// 90 CW: top-left moves to top-right
		assertThat(isBlue(thumb.getRGB(10, 10))).isTrue();
		assertThat(isRed(thumb.getRGB(181, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(181, 181))).isTrue();
	}

	@Test
	void testOversizedInputReturnsNull() throws IOException {
		var data = jpeg(quadrant());
		images.props.setImageMaxBytes(data.length - 1);

		assertThat(images.thumbnail(data)).isInstanceOf(Images.Unsupported.class);
		assertThat(images.thumbnail(new ByteArrayInputStream(data))).isInstanceOf(Images.Unsupported.class);
	}

	@Test
	void testInputStreamWithinLimit() throws IOException {
		var data = jpeg(quadrant());
		images.props.setImageMaxBytes(data.length);

		assertThat(images.thumbnail(new ByteArrayInputStream(data))).isInstanceOf(Images.Thumbnail.class);
	}

	@Test
	void testEnormousDimensionsReturnsNull() {
		assertThat(images.thumbnail(pngHeader(100_000, 100_000))).isInstanceOf(Images.Unsupported.class);
	}

	@Test
	void testPixelLimit() throws IOException {
		images.props.setImageMaxPixels(400 * 400 - 1);

		assertThat(images.thumbnail(jpeg(quadrant()))).isInstanceOf(Images.Unsupported.class);
	}

	@Test
	void testStackOverflowFallsBackToNormalOrientation() throws IOException {
		var data = withExifOrientation(jpeg(quadrant()), 6);
		images = new Images() {
			@Override
			Metadata readMetadata(byte[] imageData) {
				throw new StackOverflowError();
			}
		};
		images.props = new Props();

		assertThat(images.orientation(data)).isEqualTo(1);
		var result = thumbnail(images.thumbnail(data));
		var thumb = ImageIO.read(new ByteArrayInputStream(result));
		assertThat(isRed(thumb.getRGB(10, 10))).isTrue();
	}

	@Test
	void testGarbageIsUnsupported() {
		assertThat(images.thumbnail(new byte[]{ 1, 2, 3, 4 })).isInstanceOf(Images.Unsupported.class);
	}

	@Test
	void testTruncatedImageFails() {
		// Header only, no image data
		assertThat(images.thumbnail(pngHeader(300, 300))).isInstanceOf(Images.Failed.class);
	}

	@Test
	void testSmallImageIsAlreadySmall() throws IOException {
		assertThat(images.thumbnail(jpeg(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB))))
			.isInstanceOf(Images.AlreadySmall.class);
	}
}
