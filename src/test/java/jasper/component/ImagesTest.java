package jasper.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ImagesTest {

	Images images;

	@BeforeEach
	void init() {
		images = new Images();
	}

	private byte[] createTestImage(int width, int height) throws IOException {
		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width, height);
		g.dispose();
		
		var baos = new ByteArrayOutputStream();
		ImageIO.write(image, "png", baos);
		return baos.toByteArray();
	}

	@Test
	void testThumbnailFromInputStream() throws IOException {
		var imageData = createTestImage(400, 300);
		var thumbnail = images.thumbnail(new ByteArrayInputStream(imageData));
		
		assertThat(thumbnail).isNotNull();
		
		// Verify thumbnail is a valid image
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi).isNotNull();
		assertThat(bi.getWidth()).isLessThanOrEqualTo(192);
		assertThat(bi.getHeight()).isLessThanOrEqualTo(192);
	}

	@Test
	void testThumbnailFromByteArray() throws IOException {
		var imageData = createTestImage(400, 300);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		// Verify thumbnail is a valid image
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi).isNotNull();
	}

	@Test
	void testThumbnailLargeImage() throws IOException {
		var imageData = createTestImage(800, 600);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi.getWidth()).isLessThanOrEqualTo(192);
		assertThat(bi.getHeight()).isLessThanOrEqualTo(192);
	}

	@Test
	void testThumbnailWideImage() throws IOException {
		var imageData = createTestImage(1000, 200);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		// Wide image should be constrained by width
		assertThat(bi.getWidth()).isEqualTo(192);
		assertThat(bi.getHeight()).isLessThan(192);
	}

	@Test
	void testThumbnailTallImage() throws IOException {
		var imageData = createTestImage(200, 1000);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		// Tall image should be constrained by height
		assertThat(bi.getHeight()).isEqualTo(192);
		assertThat(bi.getWidth()).isLessThan(192);
	}

	@Test
	void testThumbnailSmallImage() throws IOException {
		// Images smaller than thumbnail size should return null (no resize needed)
		var imageData = createTestImage(100, 100);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNull();
	}

	@Test
	void testThumbnailExactSize() throws IOException {
		// Image exactly at threshold
		var imageData = createTestImage(192, 192);
		var thumbnail = images.thumbnail(imageData);
		
		// Should return null as no resize needed
		assertThat(thumbnail).isNull();
	}

	@Test
	void testThumbnailSlightlyLarger() throws IOException {
		var imageData = createTestImage(193, 193);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi.getWidth()).isLessThanOrEqualTo(192);
		assertThat(bi.getHeight()).isLessThanOrEqualTo(192);
	}

	@Test
	void testThumbnailInvalidData() {
		var invalidData = "not an image".getBytes();
		var thumbnail = images.thumbnail(invalidData);
		
		// Should return null for invalid image data
		assertThat(thumbnail).isNull();
	}

	@Test
	void testThumbnailEmptyData() {
		var emptyData = new byte[0];
		var thumbnail = images.thumbnail(emptyData);
		
		assertThat(thumbnail).isNull();
	}

	@Test
	void testThumbnailNullStream() {
		var thumbnail = images.thumbnail((java.io.InputStream) null);
		
		assertThat(thumbnail).isNull();
	}

	@Test
	void testBufferMethod() {
		var bi = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		
		// BufferedImage should return itself
		var result = Images.buffer(bi, 100, 100);
		assertThat(result).isSameAs(bi);
	}

	@Test
	void testBufferMethodWithImage() throws IOException {
		var bi = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		var scaled = bi.getScaledInstance(50, 50, Image.SCALE_SMOOTH);
		
		// Non-BufferedImage should be converted
		var result = Images.buffer(scaled, 50, 50);
		assertThat(result).isInstanceOf(BufferedImage.class);
		assertThat(result.getWidth()).isEqualTo(50);
		assertThat(result.getHeight()).isEqualTo(50);
	}

	@Test
	void testThumbnailPreservesAspectRatio() throws IOException {
		var imageData = createTestImage(800, 400); // 2:1 aspect ratio
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		// Should maintain approximately 2:1 aspect ratio
		var aspectRatio = (double) bi.getWidth() / bi.getHeight();
		assertThat(aspectRatio).isBetween(1.8, 2.2);
	}

	@Test
	void testThumbnailSquareImage() throws IOException {
		var imageData = createTestImage(500, 500);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		// Square should remain square
		assertThat(bi.getWidth()).isEqualTo(bi.getHeight());
		assertThat(bi.getWidth()).isEqualTo(192);
	}

	@Test
	void testThumbnailOutputFormat() throws IOException {
		var imageData = createTestImage(400, 300);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		// Verify it's a valid PNG
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi).isNotNull();
	}

	@Test
	void testThumbnailVeryLargeImage() throws IOException {
		// Test with a very large image
		var imageData = createTestImage(4000, 3000);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi.getWidth()).isLessThanOrEqualTo(192);
		assertThat(bi.getHeight()).isLessThanOrEqualTo(192);
	}

	@Test
	void testThumbnailExtremeAspectRatio() throws IOException {
		// Test with extreme aspect ratio (panoramic)
		var imageData = createTestImage(2000, 100);
		var thumbnail = images.thumbnail(imageData);
		
		assertThat(thumbnail).isNotNull();
		
		var bi = ImageIO.read(new ByteArrayInputStream(thumbnail));
		assertThat(bi.getWidth()).isEqualTo(192);
		assertThat(bi.getHeight()).isGreaterThan(0);
		assertThat(bi.getHeight()).isLessThan(20); // Should be very small due to aspect ratio
	}
}
