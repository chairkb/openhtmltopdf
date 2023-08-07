package com.shujutech.experiment;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.openhtmltopdf.latexsupport.LaTeXDOMMutator;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.util.Diagnostic;
import org.apache.pdfbox.io.IOUtils;
import org.w3c.dom.Element;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.extend.FSObjectDrawer;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.extend.OutputDeviceGraphicsDrawer;
import com.openhtmltopdf.extend.SVGDrawer;
import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor;
import com.openhtmltopdf.java2d.api.DefaultPageProcessor;
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import com.openhtmltopdf.mathmlsupport.MathMLDrawer;
import com.openhtmltopdf.objects.StandardObjectDrawerFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.render.DefaultObjectDrawerFactory;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.util.JDKXRLogger;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import java.io.FileOutputStream;
import java.io.OutputStream;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

public class Main 
{
		static DefaultObjectDrawerFactory buildObjectDrawerFactory() {
			DefaultObjectDrawerFactory objectDrawerFactory = new StandardObjectDrawerFactory();
			objectDrawerFactory.registerDrawer("custom/binary-tree", new SampleObjectDrawerBinaryTree());
			return objectDrawerFactory;
		}

		public static class SampleObjectDrawerBinaryTree implements FSObjectDrawer {
			int fanout;
			int angle;

			@Override
			public Map<Shape,String> drawObject(Element e, double x, double y, final double width, final double height,
												OutputDevice outputDevice, RenderingContext ctx, final int dotsPerPixel) {
				final int depth = Integer.parseInt(e.getAttribute("data-depth"));
				fanout = Integer.parseInt(e.getAttribute("data-fanout"));
				angle = Integer.parseInt(e.getAttribute("data-angle"));

				outputDevice.drawWithGraphics((float) x, (float) y, (float) width / dotsPerPixel,
						(float) height / dotsPerPixel, new OutputDeviceGraphicsDrawer() {
							@Override
							public void render(Graphics2D graphics2D) {
								double realWidth = width / dotsPerPixel;
								double realHeight = height / dotsPerPixel;
								double titleBottomHeight = 10;

								renderTree(graphics2D, realWidth / 2f, realHeight - titleBottomHeight, realHeight / depth,
										-90, depth);

								/*
								* Now draw some text using different fonts to exercise all different font
								* mappings
								*/
								Font font = Font.decode("Times New Roman").deriveFont(10f);
								if (depth == 10)
									font = Font.decode("Arial"); // Does not get mapped
								if (angle == 35)
									font = Font.decode("Courier"); // Would get mapped to Courier
								if (depth == 6)
									font = Font.decode("Dialog"); // Gets mapped to Helvetica
								graphics2D.setFont(font);
								String txt = "FanOut " + fanout + " Angle " + angle + " Depth " + depth;
								Rectangle2D textBounds = font.getStringBounds(txt, graphics2D.getFontRenderContext());
								graphics2D.setPaint(new Color(16, 133, 30));
								GradientPaint gp = new GradientPaint(10.0f, 25.0f, Color.blue,
										(float) textBounds.getWidth(), (float) textBounds.getHeight(), Color.red);
								if (angle == 35)
									graphics2D.setPaint(gp);
								graphics2D.drawString(txt, (int) ((realWidth - textBounds.getWidth()) / 2),
										(int) (realHeight - titleBottomHeight));
							}
						});
				return null;
			}

			private void renderTree(Graphics2D gfx, double x, double y, double len, double angleDeg, int depth) {
				double rad = angleDeg * Math.PI / 180f;
				double xTarget = x + Math.cos(rad) * len;
				double yTarget = y + Math.sin(rad) * len;
				gfx.setStroke(new BasicStroke(2f));
				gfx.setColor(new Color(255 / depth, 128, 128));
				gfx.draw(new Line2D.Double(x, y, xTarget, yTarget));

				if (depth > 1) {
					double childAngle = angleDeg - (((fanout - 1) * angle) / 2f);
					for (int i = 0; i < fanout; i++) {
						renderTree(gfx, xTarget, yTarget, len * 0.95, childAngle, depth - 1);
						childAngle += angle;
					}
				}
			}
		}

		private static void renderPDF(String html, PdfAConformance pdfaConformance, OutputStream outputStream) throws IOException {
				try (SVGDrawer svg = new BatikSVGDrawer();
						 SVGDrawer mathMl = new MathMLDrawer()) {
						PdfRendererBuilder builder = new PdfRendererBuilder();
						builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
						builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
						builder.defaultTextDirection(BaseRendererBuilder.TextDirection.LTR);
						builder.useSVGDrawer(svg);
						builder.useMathMLDrawer(mathMl);
						builder.addDOMMutator(LaTeXDOMMutator.INSTANCE);
						builder.useObjectDrawerFactory(buildObjectDrawerFactory());
						builder.usePdfAConformance(pdfaConformance);

						//builder.withHtmlContent(html, Main.class.getResource("/").toString());
						builder.withHtmlContent(html, null);
						builder.toStream(outputStream);
						builder.run();
				}
		}

		public static void main(String[] args) throws Exception { 
			//String xhtmlFileName = "CP22A_Pin.1_2021.xhtml";
			//String xhtmlFileName = "CP22A_Pin.1_2021_00.xhtml";
			String xhtmlFileName = "CP22A_Pin.1_2021_pdf2htmlex.html";
			try (InputStream inputStream = Main.class.getResourceAsStream("/" + xhtmlFileName)) {
					String tempDirectory = System.getProperty("java.io.tmpdir");
					File tempFile = new File(tempDirectory, "newfile.pdf");

					if (inputStream == null) System.out.println("Fail to create inputStream for file: " + xhtmlFileName);
					byte[] htmlBytes = IOUtils.toByteArray(inputStream);
					String html = new String(htmlBytes, StandardCharsets.UTF_8);
					System.out.println(html);
					try (OutputStream outputStream = new FileOutputStream(tempFile)) {
						renderPDF(html, PdfAConformance.NONE, outputStream);
						System.out.println("Successfully completed rendering: '" + xhtmlFileName + "', " + "output: " + tempFile);
				}
			}
		}
}