package pl.nask.hsn.capture.server;

public class Server {

    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Server.class);

    public Server(String[] args) {
        logger.info("PROJECT: Capture-HPC\n"
                + "VERSION: 2.5\n"
                + "DATE: Apr 25, 2008\n"
                + "COPYRIGHT HOLDER: Victoria University of Wellington, NZ\n"
                + "AUTHORS:\n"
                + "\tChristian Seifert (christian.seifert@gmail.com)\n"
                + "\tRamon Steenson(ramon.steenson@gmail.com)\n"
                + "\n"
                + "Capture-HPC is free software; you can redistribute it and/or modify\n"
                + "it under the terms of the GNU General Public License, V2 as published by\n"
                + "the Free Software Foundation.\n"
                + "\n"
                + "Capture-HPC is distributed in the hope that it will be useful,\n"
                + "but WITHOUT ANY WARRANTY; without even the implied warranty of\n"
                + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n"
                + "GNU General Public License for more details.\n"
                + "\n"
                + "You should have received a copy of the GNU General Public License\n"
                + "along with Capture-HPC; if not, write to the Free Software\n"
                + "Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301,USA\n\n");

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    String serverOption = args[++i];
                    if (serverOption.contains(":")) {
                        String[] s = serverOption.split(":");
                        if (s.length == 2) {
                            ConfigManager.getInstance().addConfigOption("server-listen-port", s[1]);
                            ConfigManager.getInstance().addConfigOption("server-listen-address", s[0]);
                        } else {
                            logger.error("ERROR in server option (-s): " + serverOption);
                        }
                    } else {
                        ConfigManager.getInstance().addConfigOption("server-listen-port", "7070");
                        ConfigManager.getInstance().addConfigOption("server-listen-address", serverOption);
                    }
                }
            } else if (args[i].equals("-h")) {
                System.out.println(getUsageString());
                System.exit(1);
            } else if (args[i].equals("-f")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("input_urls", args[++i]);
                }
            } else if (args[i].equals("-r")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("halt_on_revert", args[++i]);
                }
            } else if (args[i].equals("-a")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("upload_address", args[++i]);
                }
            } else if (args[i].equals("-p")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("sock_urls", args[++i]);
                }
            } else if (args[i].equals("-c")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("config_path", args[++i]);
                }
            } else if (args[i].equals("-o")) {
                if (((i + 1) < args.length) && !args[i + 1].startsWith("-")) {
                    ConfigManager.getInstance().addConfigOption("output_path", args[++i]);
                }
            }

        }

        /* Check that an address was specified to listen on */
        if (ConfigManager.getInstance().getConfigOption("server-listen-address") == null) {
            logger.warn("Capture Server must be run with at least the -s argument set");
            System.out.println(getUsageString());
            System.exit(1);
        }

        /* Initialise all of the singleton objects */
        EventsController.getInstance();
        VirtualMachineServerController.getInstance();
        UrlsController.getInstance();
        ClientsController.getInstance();
        ConfigManager.getInstance().loadConfigurationFile();

        if (ConfigManager.getInstance().getConfigOption("input_urls") != null) {
            String file = ConfigManager.getInstance().getConfigOption("input_urls");
            Preprocessor preprocessor = PreprocessorFactory.getDefaultPreprocessor();
            try {
                String preprocessorClassName = ConfigManager.getInstance().getConfigOption("preprocessor-classname");
                if (preprocessorClassName != null && !preprocessorClassName.equals("")) {
                    preprocessor = PreprocessorFactory.getPreprocessor(preprocessorClassName);
                    String preprocessorConfig = ConfigManager.getInstance().getConfigOption("preprocessor-configuration");
                    preprocessor.setConfiguration(preprocessorConfig);
                }
            } catch (FactoryException e) {
                logger.error("Unable to create preprocessor. Proceeding without preprocessor");
                logger.error("Exception: ", e);
            }
            preprocessor.readInputUrlsFromFile(file);
        }

        // added socket preprocessor
        if (ConfigManager.getInstance().getConfigOption("sock_urls") != null) {
            String port = ConfigManager.getInstance().getConfigOption("sock_urls");
            Preprocessor preprocessor = PreprocessorFactory.getDefaultPreprocessor();
            if (ConfigManager.getInstance().getConfigOption("upload_address") != null) {                
                preprocessor.readInputUrls(port, ConfigManager.getInstance().getConfigOption("upload_address"));
                
            } else {
                preprocessor.readInputUrls(port, "127.0.0.1");
            }
        }


    }

    private static String getUsageString() {
        StringBuffer usageString = new StringBuffer();
        usageString.append("\nUsage: java -cp [classpath] -Xmx[memory] [-DfixIds=false] -Djava.net.preferIPv4Stack=true capture.Server -s listen address -f input_urls [-r false] -c config_path.xml -o output.log -a upload_address -p upload_port");
        usageString.append("\nExample java -cp ./CaptureServer.jar -Xmx1024m -Djava.net.preferIPv4Stack=true capture.Server -s 192.168.1.100:7070 -f input_urls.txt -c config.xml -o output.log -a 127.0.1.1 -p 31337");
        return usageString.toString();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        Server server = new Server(args);
    }
}
