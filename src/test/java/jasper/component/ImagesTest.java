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
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

public class ImagesTest {

	Images images;

	@BeforeEach
	void init() {
		images = new Images();
		images.props = new Props();
	}

	BufferedImage quadrant() {
		return quadrant(400);
	}

	BufferedImage quadrant(int size) {
		// Red top-left quadrant, blue everywhere else
		var bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		var g = bi.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, size, size);
		g.setColor(Color.RED);
		g.fillRect(0, 0, size / 2, size / 2);
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

	/**
	 * Streams an RGB PNG with a red top-left quadrant and blue everywhere else,
	 * without allocating the full raster.
	 */
	byte[] largeQuadrantPng(int width, int height) throws IOException {
		var top = new byte[1 + width * 3];
		var bottom = new byte[1 + width * 3];
		for (var x = 0; x < width; x++) {
			var red = x < width / 2;
			top[1 + x * 3] = (byte) (red ? 0xFF : 0);
			top[1 + x * 3 + 2] = (byte) (red ? 0 : 0xFF);
			bottom[1 + x * 3 + 2] = (byte) 0xFF;
		}
		var idat = new ByteArrayOutputStream();
		try (var deflate = new DeflaterOutputStream(idat, new Deflater(Deflater.BEST_SPEED), 64 * 1024)) {
			for (var y = 0; y < height; y++) deflate.write(y < height / 2 ? top : bottom);
		}
		var out = new ByteArrayOutputStream();
		out.writeBytes(pngHeader(width, height));
		out.writeBytes(pngChunk("IDAT", idat.toByteArray()));
		out.writeBytes(pngChunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	byte[] pngChunk(String type, byte[] data) {
		var crc = new CRC32();
		crc.update(type.getBytes(US_ASCII));
		crc.update(data);
		var chunk = ByteBuffer.allocate(12 + data.length);
		chunk.putInt(data.length);
		chunk.put(type.getBytes(US_ASCII));
		chunk.put(data);
		chunk.putInt((int) crc.getValue());
		return chunk.array();
	}

	long allocatedBytes() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
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
	void testLargeImageSubsampled() throws IOException {
		var data = largeQuadrantPng(12_000, 9_000);

		var before = allocatedBytes();
		var result = thumbnail(images.thumbnail(data));
		var allocated = allocatedBytes() - before;

		// A full decode would allocate at least 12000 * 9000 * 3 bytes (~324 MB)
		assertThat(allocated).isLessThan(64L * 1024 * 1024);
		var thumb = ImageIO.read(new ByteArrayInputStream(result));
		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(144);
		assertThat(isRed(thumb.getRGB(10, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(181, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(10, 133))).isTrue();
	}

	BufferedImage landscape() {
		// 400x200: red left half, blue right half, green bottom-right corner
		var bi = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
		var g = bi.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, 200, 200);
		g.setColor(Color.BLUE);
		g.fillRect(200, 0, 200, 200);
		g.setColor(Color.GREEN);
		g.fillRect(300, 100, 100, 100);
		g.dispose();
		return bi;
	}

	boolean isGreen(int rgb) {
		var c = new Color(rgb, true);
		return c.getGreen() > 200 && c.getRed() < 60 && c.getBlue() < 60;
	}

	@Test
	void testNonSquareOrientation6SwapsAspectRatio() throws IOException {
		var data = withExifOrientation(jpeg(landscape()), 6);

		var thumb = ImageIO.read(new ByteArrayInputStream(thumbnail(images.thumbnail(data))));

		// 90 CW: left half moves to the top, bottom-right corner moves to bottom-left
		assertThat(thumb.getWidth()).isEqualTo(96);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isRed(thumb.getRGB(48, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(70, 110))).isTrue();
		assertThat(isGreen(thumb.getRGB(10, 181))).isTrue();
		// No empty bands from cropping
		assertThat(isRed(thumb.getRGB(2, 48))).isTrue();
		assertThat(isRed(thumb.getRGB(93, 48))).isTrue();
	}

	@Test
	void testNonSquareOrientation8SwapsAspectRatio() throws IOException {
		var data = withExifOrientation(jpeg(landscape()), 8);

		var thumb = ImageIO.read(new ByteArrayInputStream(thumbnail(images.thumbnail(data))));

		// 270 CW: left half moves to the bottom, bottom-right corner moves to top-right
		assertThat(thumb.getWidth()).isEqualTo(96);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isRed(thumb.getRGB(48, 181))).isTrue();
		assertThat(isBlue(thumb.getRGB(20, 80))).isTrue();
		assertThat(isGreen(thumb.getRGB(85, 10))).isTrue();
	}

	@Test
	void testNonSquareOrientation3KeepsAspectRatio() throws IOException {
		var data = withExifOrientation(jpeg(landscape()), 3);

		var thumb = ImageIO.read(new ByteArrayInputStream(thumbnail(images.thumbnail(data))));

		// 180: left half moves to the right, bottom-right corner moves to top-left
		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(96);
		assertThat(isRed(thumb.getRGB(181, 48))).isTrue();
		assertThat(isGreen(thumb.getRGB(10, 10))).isTrue();
	}

	/**
	 * Blue image with a green rectangle.
	 */
	BufferedImage marked(int width, int height, int gx, int gy, int gw, int gh) {
		var bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var g = bi.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.GREEN);
		g.fillRect(gx, gy, gw, gh);
		g.dispose();
		return bi;
	}

	byte[] png(BufferedImage bi) throws IOException {
		var bo = new ByteArrayOutputStream();
		ImageIO.write(bi, "png", bo);
		return bo.toByteArray();
	}

	BufferedImage read(Images.Result result) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(thumbnail(result)));
	}

	@Test
	void testExtremeWideImageCroppedToCentre() throws IOException {
		// Green ends would be visible without a centre crop
		var bi = marked(20_000, 50, 0, 0, 20_000, 50);
		var g = bi.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(9_900, 0, 200, 50);
		g.dispose();

		var thumb = read(images.thumbnail(png(bi)));

		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(48);
		assertThat(isBlue(thumb.getRGB(2, 24))).isTrue();
		assertThat(isBlue(thumb.getRGB(189, 24))).isTrue();
	}

	@Test
	void testExtremeTallImageCroppedToTop() throws IOException {
		var thumb = read(images.thumbnail(png(marked(100, 2_000, 0, 0, 100, 200))));

		// Top 400px kept: green top half, blue bottom half
		assertThat(thumb.getWidth()).isEqualTo(48);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isGreen(thumb.getRGB(24, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(24, 181))).isTrue();
	}

	@Test
	void testCropDisabledKeepsAtLeastOnePixel() throws IOException {
		images.props.setImageMaxAspectRatio(0);

		var thumb = read(images.thumbnail(png(marked(20_000, 50, 0, 0, 0, 0))));

		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(1);
	}

	@Test
	void testSmallExtremeImageIsCropped() throws IOException {
		var thumb = read(images.thumbnail(png(marked(190, 10, 0, 0, 0, 0))));

		assertThat(thumb.getWidth()).isEqualTo(40);
		assertThat(thumb.getHeight()).isEqualTo(10);
	}

	@Test
	void testSmallImageWithinRatioIsAlreadySmall() throws IOException {
		assertThat(images.thumbnail(png(marked(160, 40, 0, 0, 0, 0)))).isInstanceOf(Images.AlreadySmall.class);
	}

	@Test
	void testExtremeOrientation6CropsDisplayedTop() throws IOException {
		// Stored wide, displayed tall: stored left edge becomes displayed top
		var data = withExifOrientation(jpeg(marked(2_000, 100, 0, 0, 200, 100)), 6);

		var thumb = read(images.thumbnail(data));

		assertThat(thumb.getWidth()).isEqualTo(48);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isGreen(thumb.getRGB(24, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(24, 181))).isTrue();
	}

	@Test
	void testExtremeOrientation8CropsDisplayedTop() throws IOException {
		// Stored wide, displayed tall: stored right edge becomes displayed top
		var data = withExifOrientation(jpeg(marked(2_000, 100, 1_800, 0, 200, 100)), 8);

		var thumb = read(images.thumbnail(data));

		assertThat(thumb.getWidth()).isEqualTo(48);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isGreen(thumb.getRGB(24, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(24, 181))).isTrue();
	}

	@Test
	void testExtremeOrientation3CropsDisplayedTop() throws IOException {
		// Stored bottom edge becomes displayed top
		var data = withExifOrientation(jpeg(marked(100, 2_000, 0, 1_800, 100, 200)), 3);

		var thumb = read(images.thumbnail(data));

		assertThat(thumb.getWidth()).isEqualTo(48);
		assertThat(thumb.getHeight()).isEqualTo(192);
		assertThat(isGreen(thumb.getRGB(24, 10))).isTrue();
		assertThat(isBlue(thumb.getRGB(24, 181))).isTrue();
	}

	@Test
	void testSubsampledExifOrientationRotates() throws IOException {
		var data = withExifOrientation(jpeg(quadrant(1600)), 6);

		var thumb = ImageIO.read(new ByteArrayInputStream(thumbnail(images.thumbnail(data))));

		assertThat(thumb.getWidth()).isEqualTo(192);
		assertThat(thumb.getHeight()).isEqualTo(192);
		// 90 CW: top-left moves to top-right
		assertThat(isBlue(thumb.getRGB(10, 10))).isTrue();
		assertThat(isRed(thumb.getRGB(181, 10))).isTrue();
	}

	@Test
	void testAboveDefaultPixelLimitIsUnsupported() {
		assertThat(images.props.getImageMaxPixels()).isEqualTo(500_000_000L);

		assertThat(images.thumbnail(pngHeader(25_000, 20_001))).isInstanceOf(Images.Unsupported.class);
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
