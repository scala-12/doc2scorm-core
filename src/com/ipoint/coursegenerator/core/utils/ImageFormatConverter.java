package com.ipoint.coursegenerator.core.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.io.FileUtils;
import org.freehep.graphicsio.emf.EMFInputStream;
import org.freehep.graphicsio.emf.EMFPanel;
import org.freehep.graphicsio.emf.EMFRenderer;
import org.freehep.graphicsio.svg.SVGGraphics2D;
import org.freehep.util.io.Tag;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ipoint.coursegenerator.core.courseModel.content.blocks.contentSections.textual.paragraph.content.runs.ImageRunItem;

import net.arnx.wmf2svg.gdi.svg.SvgGdi;
import net.arnx.wmf2svg.gdi.svg.SvgGdiException;
import net.arnx.wmf2svg.gdi.wmf.WmfParseException;
import net.arnx.wmf2svg.gdi.wmf.WmfParser;

public class ImageFormatConverter {

	private final static Logger LOG = Logger.getLogger(ImageFormatConverter.class.getName());

	private final static DocumentBuilderFactory DOC_BUILD_FACTORY = DocumentBuilderFactory.newInstance();

	private final static TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

	private final static long IMG_CONVERSION_TIMEOUT = 15L;

	/**
	 * WFM to PNG conversion with use external tools
	 * 
	 * @param data
	 *            WMF image as byte array
	 * @param width
	 * @param height
	 * @param pathToSOffice
	 *            Path to LibreOffice
	 * @return PNG as byte array
	 */
	public static byte[] transcodeWmfToPng(byte[] data, Optional<File> pathToSOffice) {
		byte[] pngData = null;
		try {
			final SvgGdi gdi = new SvgGdi(false);
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			new WmfParser().parse(in, gdi);

			float height = ImageRunItem
					.toPxSize(gdi.getDocument().getFirstChild().getAttributes().getNamedItem("height").getNodeValue());
			float width = ImageRunItem
					.toPxSize(gdi.getDocument().getFirstChild().getAttributes().getNamedItem("width").getNodeValue());

			if (pathToSOffice.isPresent() && pathToSOffice.get().exists()) {
				pngData = transcodeImgToPng(data, "wmf", width, height, pathToSOffice);
			} else {
				// Do it when LibreOffice is not installed (internal tools)
				Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
				ByteArrayOutputStream svgBOS = new ByteArrayOutputStream();
				StreamResult convertedSvgSR = new StreamResult(svgBOS);
				transformer.transform(new DOMSource(gdi.getDocument()), convertedSvgSR);
				svgBOS.close();
				ByteArrayInputStream svgBIS = new ByteArrayInputStream(svgBOS.toByteArray());

				String docParser = XMLResourceDescriptor.getXMLParserClassName();
				Document svgData = new SAXSVGDocumentFactory(docParser)
						.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, svgBIS);

				pngData = transcodeSvgToPng(svgData, width, height);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SvgGdiException | WmfParseException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		return pngData;
	}

	/**
	 * EFM to PNG conversion with use external tools
	 * 
	 * @param data
	 *            EMF image as byte array
	 * @param width
	 * @param height
	 * @param pathToSOffice
	 *            Path to LibreOffice
	 * @return PNG as byte array
	 */
	public static byte[] transcodeEmfToPng(byte[] data, Optional<File> pathToSOffice) {
		byte[] pngData = null;
		try (ByteArrayInputStream emfBIS = new ByteArrayInputStream(data)) {
			EMFInputStream emfIS = new EMFInputStream(emfBIS);
			float width = (float) emfIS.readHeader().getBounds().getWidth();
			float height = (float) emfIS.readHeader().getBounds().getHeight();
			if (pathToSOffice.isPresent() && pathToSOffice.get().exists()) {
				pngData = transcodeImgToPng(data, "emf", width, height, pathToSOffice);
			} else {
				EMFRenderer emfRenderer = new EMFRenderer(emfIS);
				EMFPanel emfPanel = new EMFPanel();
				emfPanel.setRenderer(emfRenderer);

				ByteArrayOutputStream svgBOS = new ByteArrayOutputStream();
				SVGGraphics2D svgGraphics = new SVGGraphics2D(svgBOS, emfPanel);
				svgGraphics.setDeviceIndependent(true);
				svgGraphics.startExport();
				emfPanel.print(svgGraphics);
				svgGraphics.endExport();

				try (ByteArrayInputStream svgBIS = new ByteArrayInputStream(
						svgBOS.toString(StandardCharsets.UTF_8.name()).getBytes())) {
					Document svgDoc = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
							.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, svgBIS);
					repairSvgTextPosition(svgDoc, getEmfNodeWithAllTextNodes(data));

					pngData = transcodeSvgToPng(svgDoc, width, height);
				}
			}
		} catch (IOException e1) {
			LOG.warning("Input data can't be rendered to EMF");
			e1.printStackTrace();
		}

		return pngData;
	}

	private static byte[] transcodeImg(byte[] data, String sourceExt, String destExt, Optional<File> pathToSOffice) {
		byte[] destData = null;
		if (pathToSOffice.isPresent() && pathToSOffice.get().exists()) {
			File imgFile = null;
			File destFile = null;
			try {
				String fileName = UUID.randomUUID().toString();
				imgFile = File.createTempFile("img", fileName + "." + sourceExt);
				FileUtils.writeByteArrayToFile(imgFile, data);

				destFile = new File(imgFile.getParentFile(),
						imgFile.getName().substring(0, imgFile.getName().length() - sourceExt.length() - 1) + "."
								+ destExt);
				destFile.createNewFile();

				ArrayList<String> cmd = new ArrayList<>();
				cmd.add(pathToSOffice.get().getAbsolutePath());
				cmd.add("--invisible");
				cmd.add("--convert-to");
				cmd.add(destExt);
				cmd.add("--outdir");
				cmd.add(destFile.getParentFile().getAbsolutePath());
				cmd.add(imgFile.getAbsolutePath());

				ProcessBuilder pb = new ProcessBuilder(cmd);
				Process proc = pb.start();

				if (proc.waitFor(IMG_CONVERSION_TIMEOUT, TimeUnit.SECONDS)) {
					if (destFile.length() == 0) {
						LOG.warning("Image was not converted to " + destExt);
					} else {
						LOG.info("Image was converted to " + destExt + ": " + destFile.getAbsolutePath());
						destData = FileUtils.readFileToByteArray(destFile);
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			} finally {
				if (imgFile != null) {
					imgFile.delete();
				}
				if (destFile != null) {
					destFile.delete();
				}
			}
		}

		return destData;
	}

	private static byte[] transcodeImgToPng(byte[] data, String sourceExt, float realWidth, float realHeight,
			Optional<File> pathToSOffice) {
		byte[] destImgData = null;
		try {
			String destExt = "png";
			byte[] sourceImgData = transcodeImg(data, sourceExt, destExt, pathToSOffice);
			BufferedImage sourceImg = ImageIO.read(new ByteArrayInputStream(sourceImgData));
			float sourceHeight = sourceImg.getHeight();
			float sourceWidth = sourceImg.getWidth();
			float sourceFormat = sourceWidth / sourceHeight;
			float realFormat = realWidth / realHeight;
			if ((sourceFormat > (0.9 * realFormat)) && (sourceFormat < (1.1 * realFormat))) {
				destImgData = sourceImgData;
			} else {
				float shift = ("emf".equals(sourceExt)) ? 300f : 0;
				float scaledHeight = sourceHeight - shift;
				if (scaledHeight > realHeight) {
					scaledHeight = realHeight;
				}

				float scaledWidth = sourceWidth - shift;
				if (scaledWidth > realWidth) {
					scaledWidth = realWidth;
				}

				if (realFormat < 1) {
					scaledWidth = scaledHeight * realFormat;
				} else {
					scaledHeight = scaledWidth / realFormat;
				}

				int yPos = (int) ((sourceHeight - scaledHeight) / 2);
				int xPos = (int) ((sourceWidth - scaledWidth) / 2);

				BufferedImage destImg = sourceImg.getSubimage(xPos, yPos, (int) scaledWidth, (int) scaledHeight);
				ByteArrayOutputStream destImgBOS = new ByteArrayOutputStream();
				ImageIO.write(destImg, destExt, destImgBOS);
				destImgData = destImgBOS.toByteArray();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return destImgData;
	}

	// TODO: Remove this annotation if used
	@SuppressWarnings("unused")
	private static Document transcodeImgToSvg(byte[] data, String sourceExt, Optional<File> pathToSOffice) {
		Document svgDoc = null;
		byte[] svgData = transcodeImg(data, sourceExt, "svg", pathToSOffice);
		if (svgData != null) {
			try (ByteArrayInputStream svgBIS = new ByteArrayInputStream(svgData)) {
				svgDoc = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
						.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, svgBIS);
				LOG.info("SVG image was parsed to svg document");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return svgDoc;
	}

	/**
	 * WFM to PNG conversion with use internal tools
	 * 
	 * @param data
	 *            WMF image as byte array
	 * @return PNG as byte array
	 */
	public static byte[] transcodeWmfToPng(byte[] data) {
		return transcodeWmfToPng(data, null);
	}

	/**
	 * EFM to PNG conversion with use internal tools
	 * 
	 * @param data
	 *            EMF image as byte array
	 * @return PNG as byte array
	 */
	public static byte[] transcodeEmfToPng(byte[] data) {
		return transcodeEmfToPng(data, null);
	}

	private static byte[] transcodeSvgToPng(Document svg, Float width, Float height) {
		Document opensymbol = GuardianCharacters.getCharactersData();

		if (opensymbol == null) {
			LOG.warning("File \"opensymbol.svg\" is not initialized");
		} else {
			// TODO: what is it?
			Node opensymbolDefs = svg.importNode(opensymbol.getElementsByTagName("defs").item(0), true);
			// if (svg.getElementsByTagName("defs").getLength() != 0) {
			// Node svgDefs = svg.createElement("defs");
			// while (svgDefs.hasChildNodes()) {
			// opensymbolDefs.appendChild(svgDefs.getFirstChild());
			// }
			// svg.removeChild(svgDefs);
			// }
			svg.getDocumentElement().appendChild(opensymbolDefs);

			((Element) svg.getElementsByTagName("g").item(0)).setAttribute("style",
					"font-family:OpenSymbol; fill:black;");
		}

		try {
			NodeList textNodes = svg.getElementsByTagName("text");
			try {
				String nodeText;
				for (int i = 0; i < textNodes.getLength(); i++) {
					Node textNode = textNodes.item(i);
					nodeText = textNode.getTextContent();
					if (nodeText != null) {
						nodeText = nodeText.replaceAll(
								new String(new byte[] { (byte) 0xC2, (byte) 0xB3 }, StandardCharsets.UTF_8), "\u2265");
						nodeText = nodeText.replaceAll(
								new String(new byte[] { (byte) 0xC2, (byte) 0xB6 }, StandardCharsets.UTF_8), "\u2202");
						nodeText = nodeText.replaceAll(
								new String(new byte[] { (byte) 0xC3, (byte) 0xB2 }, StandardCharsets.UTF_8), "\u222b");

						textNode.setTextContent(nodeText);
					}
				}
			} catch (DOMException e) {
				e.printStackTrace();
			}

			TranscoderInput trcoderInput = new TranscoderInput(svg);
			ByteArrayOutputStream pngBOS = new ByteArrayOutputStream();
			TranscoderOutput trcoderOutput = new TranscoderOutput(pngBOS);
			PNGTranscoder transcoder = new PNGTranscoder();

			if (((width != null) && (width > 0)) || ((height != null) && (height > 0))) {
				Map<TranscodingHints.Key, Object> hints = new HashMap<TranscodingHints.Key, Object>();
				if ((width != null) && (width > 0)) {
					hints.put(PNGTranscoder.KEY_WIDTH, width);
				}
				if ((height != null) && (height > 0)) {
					hints.put(PNGTranscoder.KEY_HEIGHT, height);
				}
				hints.put(PNGTranscoder.KEY_XML_PARSER_VALIDATING, false);
				transcoder.setTranscodingHints(hints);
			}
			transcoder.transcode(trcoderInput, trcoderOutput);
			pngBOS.flush();
			pngBOS.close();

			return pngBOS.toByteArray();
		} catch (TranscoderException e) {
			LOG.warning("SVG can't be converted to PNG");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	private static Node getEmfNodeWithAllTextNodes(byte[] emfBytes) {
		ByteArrayInputStream emfBIS = new ByteArrayInputStream(emfBytes);
		EMFInputStream emfIS = new EMFInputStream(emfBIS);
		ByteArrayInputStream emfPartBIS = null;

		try {
			Tag emfTag;
			StringBuilder emfAsStr = new StringBuilder();
			String emfTagStr = null;
			String emfTagVal = null;

			emfAsStr.append("<emfPart>");
			// searching of text nodes with some attributes: bounds, string, pos
			while ((emfTag = emfIS.readTag()) != null) {
				if ("ExtTextOutW".equalsIgnoreCase(emfTag.getName())) {
					emfTagStr = emfTag.toString().toLowerCase();
					emfAsStr.append("<").append("text").append(" ");
					int attStart;
					if ((attStart = emfTagStr.indexOf("bounds")) != -1) {
						attStart += emfTagStr.substring(attStart).indexOf("[") + 1;
						emfTagVal = emfTagStr.substring(attStart,
								emfTagStr.substring(attStart).indexOf("]") + attStart);
						int sepPos;
						while (((sepPos = (emfTagVal.indexOf(","))) != -1) || (!emfTagVal.isEmpty())) {
							int eqPos = emfTagVal.indexOf("=");
							if (sepPos == -1) {
								sepPos = emfTagVal.length();
							}
							emfAsStr.append("bounds-").append(emfTagVal.substring(0, eqPos)).append("=\"")
									.append(emfTagVal.substring(eqPos + 1, sepPos)).append("\" ");
							emfTagVal = emfTagVal.substring((sepPos == emfTagVal.length()) ? sepPos : sepPos + 1);
						}
					}
					if ((attStart = emfTagStr.indexOf("string")) != -1) {
						attStart += "string: ".length();
						emfAsStr.append("string=\"").append(emfTag.toString().substring(attStart,
								emfTagStr.substring(attStart).indexOf("\n") + attStart)).append("\" ");
					}
					if ((attStart = emfTagStr.indexOf("pos")) != -1) {
						attStart += emfTagStr.substring(attStart).indexOf("[") + 1;
						emfTagVal = emfTagStr.substring(attStart,
								emfTagStr.substring(attStart).indexOf("]") + attStart);
						int sepPos;
						while (((sepPos = (emfTagVal.indexOf(","))) != -1) || (!emfTagVal.isEmpty())) {
							int eqPos = emfTagVal.indexOf("=");
							if (sepPos == -1) {
								sepPos = emfTagVal.length();
							}
							emfAsStr.append("pos-").append(emfTagVal.substring(0, eqPos)).append("=\"")
									.append(emfTagVal.substring(eqPos + 1, sepPos)).append("\" ");
							emfTagVal = emfTagVal.substring((sepPos == emfTagVal.length()) ? sepPos : sepPos + 1);
						}
					}
					emfAsStr.append("/>");
				}
			}

			emfAsStr.append("</emfPart>");
			emfPartBIS = new ByteArrayInputStream(emfAsStr.toString().getBytes());
			DocumentBuilder builder = DOC_BUILD_FACTORY.newDocumentBuilder();

			return builder.parse(emfPartBIS).getElementsByTagName("emfPart").item(0);
		} catch (IOException | SAXException e) {
			// EMF can't be converted in document
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} finally {
			try {
				emfBIS.close();
				emfIS.close();
				if (emfPartBIS != null) {
					emfPartBIS.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	private static void repairSvgTextPosition(Document svg, Node emfDocPart) {
		if ((svg != null) && (emfDocPart != null)) {
			NodeList svgTextNodes = svg.getElementsByTagName("text");

			for (int i = 0; i < svgTextNodes.getLength(); ++i) {
				Node svgNode = svgTextNodes.item(i);

				if (svgNode.hasAttributes()) {
					Node emfNode = emfDocPart.getFirstChild();
					boolean isEqual = false;
					while (!isEqual && (emfNode != null)) {
						if (svgNode.getTextContent()
								.equals(emfNode.getAttributes().getNamedItem("string").getNodeValue())) {
							isEqual = true;
						} else {
							emfNode = emfNode.getNextSibling();
						}
					}

					if (isEqual) {
						if (((svgNode.getAttributes().getNamedItem("x") != null)
								&& svgNode.getAttributes().getNamedItem("x").getNodeValue().equals("0"))
								|| (svgNode.getAttributes().getNamedItem("x") == null)) {
							float posX = 0;
							if (emfNode.getAttributes().getNamedItem("bounds-x") != null) {
								posX = Float
										.parseFloat((emfNode.getAttributes().getNamedItem("bounds-x").getNodeValue()));
							}
							if (emfNode.getAttributes().getNamedItem("pos-x") != null) {
								posX -= Float.parseFloat(emfNode.getAttributes().getNamedItem("pos-x").getNodeValue());
							}

							svgNode.getAttributes().getNamedItem("x").setNodeValue(String.valueOf(posX));
						}
						if (((svgNode.getAttributes().getNamedItem("y") != null)
								&& svgNode.getAttributes().getNamedItem("y").getNodeValue().equals("0"))
								|| (svgNode.getAttributes().getNamedItem("y") == null)) {
							float posY = 0;
							if (emfNode.getAttributes().getNamedItem("bounds-y") != null) {
								posY = Float
										.parseFloat((emfNode.getAttributes().getNamedItem("bounds-y").getNodeValue()));
							}

							svgNode.getAttributes().getNamedItem("y").setNodeValue(String.valueOf(posY));
						}

						// if repaired then don't use this node in future
						emfDocPart.removeChild(emfNode);
					}
				}
			}
		}
	}
}
