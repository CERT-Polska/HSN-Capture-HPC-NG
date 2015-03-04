package pl.nask.hsn.capture.server;

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

import javax.xml.*;
import javax.xml.validation.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.*;
import org.xml.sax.*;


public class ConfigFile implements Observer, ErrorHandler {
    private Document configDocument;
    private boolean loaded;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ConfigFile.class);

    public ConfigFile() {
        loaded = false;
    }

    public void parse(String file) {
        ConfigManager.getInstance().addObserver(this);
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        //factory.

        try {
            factory.newDocumentBuilder();
            DocumentBuilder builder = factory.newDocumentBuilder();
            configDocument = builder.parse(file);

            /* Validate the config file */
            SchemaFactory factory2 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Source schemaFile = new StreamSource(new File("config.xsd"));
            Schema schema = factory2.newSchema(schemaFile);
            Validator validator = schema.newValidator();
            validator.setErrorHandler(this);

            try {
                logger.info("Validating " + file + " ...");
                validator.validate(new DOMSource(configDocument));
                logger.info(file + " successfully validated");
            } catch (SAXException e) {
                logger.error("Exception: ", e);
                System.exit(1);
            }

            this.parseGlobalElements();
            this.parseExclusionListElements();
            this.parseServerElements();
            logger.info("PARSING PREPROCESSOR");
            this.parsePreprocessorElements();
            loaded = true;
        } catch (SAXException e) {
            logger.error("Exception: ", e);
            System.exit(1);
        } catch (IOException e) {
            logger.error("Exception: ", e);
            System.exit(1);
        } catch (ParserConfigurationException e) {
            logger.error("Exception: ", e);
            System.exit(1);
        }
    }

    private void parseExclusionListElements() {
        NodeList exclusionLists = configDocument.getElementsByTagName("exclusion-list");
        for (int i = 0; i < exclusionLists.getLength(); i++) {
            Element e = new Element();
            NamedNodeMap serverMap = exclusionLists.item(i).getAttributes();
            e.name = "exclusion-list";
            e.attributes.put("add-list", "");
            e.attributes.put("file", serverMap.getNamedItem("file").getNodeValue());
            e.attributes.put("monitor", serverMap.getNamedItem("monitor").getNodeValue());

            EventsController.getInstance().notifyEventObservers(e);
        }
    }

    public void parseGlobalElements() {
        NodeList globalList = configDocument.getElementsByTagName("global");

        for (int i = 0; i < globalList.getLength(); i++) {
            NamedNodeMap attributesMap = globalList.item(i).getAttributes();
            for (int k = 0; k < attributesMap.getLength(); k++) {
                String option = attributesMap.item(k).getNodeName();
                String value = attributesMap.item(k).getNodeValue();
                ConfigManager.getInstance().addConfigOption(option, value);
            }
        }
    }

    public void parsePreprocessorElements() {
        NodeList preprocessor = configDocument.getElementsByTagName("preprocessor");
        Node n = preprocessor.item(0);

        if (n != null) {
            logger.info("n is not null");
            String value = n.getAttributes().getNamedItem("classname").getNodeValue();
            ConfigManager.getInstance().addConfigOption("preprocessor-classname", value);

            String configValue = n.getFirstChild().getNextSibling().getNodeValue();
            logger.info("CONFIG: " + configValue);
            ConfigManager.getInstance().addConfigOption("preprocessor-configuration", configValue);
        } else {

        }
    }

    public void parseServerElements() {
        NodeList globalList = configDocument.getElementsByTagName("virtual-machine-server");
        for (int i = 0; i < globalList.getLength(); i++) {
            Element event = new Element();
            NamedNodeMap serverMap = globalList.item(i).getAttributes();
            event.name = "virtual-machine-server";
            event.attributes.put("add", "");
            event.attributes.put("type", serverMap.getNamedItem("type").getNodeValue());
            event.attributes.put("address", serverMap.getNamedItem("address").getNodeValue());
            event.attributes.put("vmsid", serverMap.getNamedItem("vmsid").getNodeValue());
            event.attributes.put("port", serverMap.getNamedItem("port").getNodeValue());
            event.attributes.put("username", serverMap.getNamedItem("username").getNodeValue());
            event.attributes.put("password", serverMap.getNamedItem("password").getNodeValue());
            NodeList vmElements = globalList.item(i).getChildNodes();
            for (int k = 0; k < vmElements.getLength(); k++) {
                if (vmElements.item(k).getNodeName().equals("virtual-machine")) {
                    Element childEvent = new Element();
                    NamedNodeMap vmMap = vmElements.item(k).getAttributes();
                    childEvent.name = "virtual-machine";
                    childEvent.attributes.put("vm-path", vmMap.getNamedItem("vm-path").getNodeValue());
                    childEvent.attributes.put("vmid", vmMap.getNamedItem("vmid").getNodeValue());
                    event.childElements.add(childEvent);
                }
            }
            EventsController.getInstance().notifyEventObservers(event);
        }
    }


    public void update(Observable sender, Object option) {
        if (loaded) {
            if (((String) option).equals("server")) {

            } else {
                NodeList globalList = configDocument.getElementsByTagName("global");
                for (int i = 0; i < globalList.getLength(); i++) {
                    NamedNodeMap optionsMap = globalList.item(i).getAttributes();
                    Node nodeOption = optionsMap.getNamedItem((String) option);

                    nodeOption.setNodeValue((String) ConfigManager.getInstance().getConfigOption((String) option));
                    this.saveConfigFile();
                }
            }
        }
    }

    public void saveConfigFile() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.transform(new DOMSource(configDocument), new StreamResult(new File("config.xml")));
        } catch (TransformerConfigurationException e) {
            logger.error("Exception: ", e);
        } catch (TransformerException e) {
            logger.error("Exception: ", e);
        }
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        logger.error("Error occured during parsing of config.xml\n");
        logger.error(exception.getMessage() + " Line: " +
                exception.getLineNumber() + " Column: " + exception.getColumnNumber());
        logger.error("Exception: ", exception);
        System.exit(1);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        logger.error("Fatal Error occured during parsing of config.xml\n");
        logger.error(exception.getMessage());
        logger.error("Exception: ", exception);
        System.exit(1);
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        logger.error("Warning occured during parsing of config.xml\n");
        logger.error(exception.getMessage());
        logger.error("Exception: ", exception);
    }
}
