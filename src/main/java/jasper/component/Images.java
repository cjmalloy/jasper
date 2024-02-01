package jasper.component;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Profile("storage")
@Component
public class Images {
	private static final Logger logger = LoggerFactory.getLogger(Images.class);
	private static final int THUMBNAIL_SIZE = 192;

	@Autowired
	Ingest ingest;

	@Autowired
	Storage storage;

	@Timed(value = "jasper.images")
	public byte[] thumbnail(InputStream image) {
		try {
			var bi = ImageIO.read(image);
			var bo = new ByteArrayOutputStream();
			var width = bi.getWidth();
			var height = bi.getHeight();
			if (width > THUMBNAIL_SIZE && height > THUMBNAIL_SIZE) {
				double ar = (double) width / height;
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
				// Treat resizing an existing thumbnail as an error
				return null;
			}
			return bo.toByteArray();
		} catch (Exception e) {
			logger.debug("Error resizing thumbnail", e);
			return null;
		}
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
