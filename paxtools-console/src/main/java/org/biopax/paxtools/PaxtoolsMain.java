package org.biopax.paxtools;


import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.simpleIO.SimpleReader;
import org.biopax.paxtools.io.simpleIO.SimpleEditorMap;
import org.biopax.paxtools.io.sif.SimpleInteractionConverter;
import org.biopax.paxtools.io.sif.level2.ComponentRule;
import org.biopax.paxtools.io.sif.level2.ConsecutiveCatalysisRule;
import org.biopax.paxtools.io.sif.level2.ControlRule;
import org.biopax.paxtools.io.sif.level2.ControlsTogetherRule;
import org.biopax.paxtools.io.sif.level2.ParticipatesRule;
import org.biopax.paxtools.io.simpleIO.SimpleExporter;
import org.biopax.paxtools.io.gsea.GSEAConverter;
import org.biopax.paxtools.controller.*;
import org.biopax.paxtools.converter.OneTwoThree;
import org.biopax.paxtools.model.*;
import org.biopax.validator.BiopaxValidatorClient;
import org.mskcc.psibiopax.converter.PSIMIBioPAXConverter;
import org.mskcc.psibiopax.converter.driver.PSIMIBioPAXConverterDriver;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;

/**
 * A command line accessible utility for basic Paxtools functionalities.
 *
 *  Usage: CLASS_NAME [options]
 *  Avaliable operations:
 *      --merge file1 file2 output		merges file2 into file1 and writes it into output\n"
 *      --to-sif file1 output			converts model to simple interaction format
 *      --validate path out xml|html	validates BioPAX model file (or all the files in the directory), outputs xml or html
 *      --integrate file1 file2 output	integrates file2 into file1 and writes it into output (experimental)
 *      --to-level3 file1 output		converts level 1 or 2 to the level 3 file
 *      --psimi-to level file1 output	converts PSI-MI Level 2.5 to biopax level 2 or 3 file
 *      --to-GSEA file1 output database crossSpeciesCheck" converts level 1 or 2 or 3 to GSEA output.
 *      Searches database for participant id or uses biopax rdf id if database is NONE.
 *      Cross species check ensures participant protein is from same species as pathway (set to true or false).
 *      --fetch file1 id1,id2,.. output	extracts a sub-model from the file1 and writes BioPAX to output\n"
 *      --help							prints this screen and exits"
 *
 */
public class PaxtoolsMain {

    public static Log log = LogFactory.getLog(PaxtoolsMain.class);
    private final static String CLASS_NAME = "PaxtoolsMain";

    public static void main(String[] argv) throws IOException, InvocationTargetException, IllegalAccessException {


        if( argv.length < 1 ) {
            showHelp();
        }

        BioPAXIOHandler io = new SimpleReader();
        
        for(int count = 0; count < argv.length; count++) {
            if( argv[count].equals("--help") ) {
                showHelp();
            } else if( argv[count].equals("--merge") ) {
                if( argv.length <= count+3 )
                    showHelp();
                
                Model model1 = getModel(io, argv[count+1]);
                Model model2 = getModel(io, argv[count+2]);

                Merger merger = new Merger(new SimpleEditorMap());
                merger.merge(model1, model2);

                SimpleExporter simpleIO = new SimpleExporter(model1.getLevel());
                simpleIO.convertToOWL(model1, new FileOutputStream(argv[count+3]));

            } else if( argv[count].equals("--integrate") ) {
                if( argv.length <= count+3 )
                    showHelp();
                
                Model model1 = getModel(io, argv[count+1]);
                Model model2 = getModel(io, argv[count+2]);

                Integrator integrator = 
                	new Integrator(new SimpleEditorMap(), model1, model2);
                integrator.integrate();

                SimpleExporter simpleIO = new SimpleExporter(model1.getLevel());
                simpleIO.convertToOWL(model1, new FileOutputStream(argv[count+3]));

            } else if( argv[count].equals("--to-sif") ) {
                if( argv.length <= count+2 )
                    showHelp();

                Model model = getModel(io, argv[count+1]);

                SimpleInteractionConverter sic = null;
				if (BioPAXLevel.L2.equals(model.getLevel())) {
					sic = new SimpleInteractionConverter(new ComponentRule(),
							new ConsecutiveCatalysisRule(), new ControlRule(),
							new ControlsTogetherRule(), new ParticipatesRule());
				} else if (BioPAXLevel.L3.equals(model.getLevel())) {
					sic = new SimpleInteractionConverter(
							new org.biopax.paxtools.io.sif.level3.ComponentRule(),
							new org.biopax.paxtools.io.sif.level3.ConsecutiveCatalysisRule(),
							new org.biopax.paxtools.io.sif.level3.ControlRule(),
							new org.biopax.paxtools.io.sif.level3.ControlsTogetherRule(),
							new org.biopax.paxtools.io.sif.level3.ParticipatesRule());
				} else {
        			System.err.println("SIF converter does not yet support BioPAX level: " 
        					+ model.getLevel());
        			System.exit(0);
				}
				
                sic.writeInteractionsInSIF(model, new FileOutputStream(argv[count+2]));

            } else if( argv[count].equals("--validate") ) {
              	if(argv.length <= count+3) 
              		showHelp(); // and exit
            	
                String name = argv[count + 1];
				String out = argv[count + 2];
				boolean isGetHtml;
				if("html".equalsIgnoreCase(argv[count + 3])) 
				{
					isGetHtml = true;
					out += ".htm";
					
				} else {
					isGetHtml = false;
					out += ".xml";
				}
                
                Collection<File> files = new HashSet<File>();
				File fileOrDir = new File(name);
				if (!fileOrDir.canRead()) {
					System.out.println("Cannot read " + name);
					System.exit(-1);
				}
				
				// collect files
				if (fileOrDir.isDirectory()) {
					// validate all the OWL files in the folder
					FilenameFilter filter = new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return (name.endsWith(".owl"));
						}
					};
					for (String s : fileOrDir.list(filter)) {
						files.add(new File(fileOrDir.getCanonicalPath()
								+ File.separator + s));
					}
				} else {
					files.add(fileOrDir);
				}
				
				// upload and validate using the default URL:
				// http://www.biopax.org/biopax-validator/validator/fileUpload.html
				OutputStream os = new FileOutputStream(out);
				try {
					if (!files.isEmpty()) {
        				BiopaxValidatorClient val = 
        					new BiopaxValidatorClient(null, isGetHtml);
        				val.validate(files.toArray(new File[]{}), os);
        			} 
				} catch (Exception ex) {
					// fall-back: not using the remote validator; trying to read files
					String msg = "Faild to Validate Using the Remote Service.\n " +
					"Now Trying To Read Each File and Build The Model\n" +
					"Watch Log Messages...\n";
					System.err.println(msg);
					os.write(msg.getBytes());
					
					for(File f : files) {
						Model m = null;
						msg ="";
		                try {
		                    m = io.convertFromOWL(new FileInputStream(f));
		                    msg = "Model that contains " 
		                		+ m.getObjects().size()
		                		+ " elements is created (check the log messages)\n";
		                    os.write(msg.getBytes());
		                } catch(Exception e)
		                {
		                	msg = "Error during validation" + e + "\n";
		                    os.write(msg.getBytes());
		                    e.printStackTrace();
		                    log.error(msg);
		                }
		                os.flush();
					}
				}

            } else if( argv[count].equals("--to-level3") ) {
                if( argv.length <= count+2 )
                    showHelp();
				SimpleReader reader = new SimpleReader();
				Model model = reader.convertFromOWL(new FileInputStream(
						argv[count+1]));
				model = (new OneTwoThree()).filter(model);
				if (model != null) {
					SimpleExporter exporter = new SimpleExporter(model
							.getLevel());
					exporter.convertToOWL(model, new FileOutputStream(argv[count+2]));
				}
            } else if( argv[count].equals("--psimi-to")) {
        		// some utility info
        		System.err.println("PSI-MI to BioPAX Conversion Tool v2.0");
        		System.err.println("Supports PSI-MI Level 2.5 (compact) model and BioPAX Level 2 or 3.");

                if( argv.length <= count+3 )
                    showHelp();

        		// check args - proper bp level
        		Integer bpLevelArg = null;
        		try {
        			bpLevelArg = Integer.valueOf(argv[count+1]);
        			if (bpLevelArg != 2 && bpLevelArg != 3) {
        				throw new NumberFormatException();
        			}
        		}
        		catch (NumberFormatException e) {
        			System.err.println("Incorrect BioPAX level specified: " + argv[count+1] + " .  Please select level 2 or 3.");
        			System.exit(0);
        		}

        		// set strings vars
        		String inputFile = argv[count+2];
        		String outputFile = argv[count+3];

        		// check args - input file exists
        		if (!((File)(new File(inputFile))).exists()) {
        			System.err.println("input filename: " +inputFile + " does not exist!");
        			System.exit(0);
        		}

        		// create converter and convert file
        		try {
        			// set bp level
        			BioPAXLevel bpLevel = (bpLevelArg == 2) ? BioPAXLevel.L2 : BioPAXLevel.L3;

        			// create input/output streams
        			FileInputStream fis = new FileInputStream(inputFile);
        			FileOutputStream fos = new FileOutputStream(outputFile);

        			// create converter
        			PSIMIBioPAXConverterDriver.checkPSILevel(inputFile);
        			PSIMIBioPAXConverter converter = new PSIMIBioPAXConverter(bpLevel);

        			// note streams will be closed by converter
        			converter.convert(fis, fos);
        		}
        		catch (Exception e) {
        			e.printStackTrace();
        			System.exit(0);
        		}
            }
            else if (argv[count].equals("--to-GSEA")) {
            	if (argv.length != count+5) {
            		showHelp();
            	}
            	Model model = (new SimpleReader()).convertFromOWL(new FileInputStream(argv[count+1]));
            	(new GSEAConverter(argv[count+3], new Boolean(argv[count+4]))).writeToGSEA(model, new FileOutputStream(argv[count+2]));
            } else if( argv[count].equals("--fetch") ) {
                if( argv.length <= count+3 )
                    showHelp();
                
                // set strings vars
        		String in = argv[count+1];
        		String[] ids = argv[count+2].split(",");
        		String out = argv[count+3];
        		
				Model model = (new SimpleReader()).convertFromOWL(new FileInputStream(in));
				exportModel(new FileOutputStream(out), model, ids);
            }
            
        }
    }

    
	public static void exportModel(OutputStream outputStream, Model model, String... ids) 
	{
		Model newModel;
		
		if (ids.length > 0) {
			newModel = model.getLevel().getDefaultFactory().createModel();
			String base = model.getNameSpacePrefixMap().get("");
			newModel.getNameSpacePrefixMap().put("", base);
			PropertyFilter filter = new PropertyFilter() {
				public boolean filter(PropertyEditor editor) {
					return !"nextStep".equalsIgnoreCase(editor.getProperty());
				}
			};
			
			Fetcher fetcher = new Fetcher(
					new SimpleEditorMap(model.getLevel()), filter);
			for(String uri : ids) {
				BioPAXElement bpe = model.getByID(uri);
				if(bpe != null) {
					fetcher.fetch(bpe, newModel);
				}
			}
		} else {
			newModel = model; // export everything!
		}
		
		try {
			new SimpleExporter(model.getLevel()).convertToOWL(newModel, outputStream);
		} catch (Exception e) {
			throw new RuntimeException("Failed to export Model.", e);
		}
	}
    
    
    private static void showHelp() {
        System.err.println(
                    "Invalid usage: " + CLASS_NAME + "\n"
                +   "\n"
                +   "Avaliable operations:\n"
                +   "--merge file1 file2 output" +			"\t\tmerges file2 into file1 and writes it into output\n"
                +   "--to-sif file1 output" +				"\t\t\tconverts model to simple interaction format\n"
                +   "--validate path out xml|html" +		"\t\tvalidates the BioPAX file (or all the files in the directory), outputs xml or html\n"
                +   "--integrate file1 file2 output" +		"\t\tintegrates file2 into file1 and writes it into output (experimental)\n"
                +   "--to-level3 file1 output"	+			"\t\tconverts level 1 or 2 to the level 3 file\n"
                +	"--psimi-to level file1 output" +		"\t\tconverts PSI-MI Level 2.5 to biopax level 2 or 3 file\n"
                +	"--to-GSEA file1 output database crossSpeciesCheck" + "\t\tconverts level 1 or 2 or 3 to GSEA output.\n"
                +   "                                                 " + "\t\tSearches database for participant id or uses biopax rdf id if database is \"NONE\".\n"
                +   "                                                 " + "\t\tCross species check ensures participant protein is from same species as pathway (set to true or false).\n"
                +	"--fetch file1 id1,id2,.. output" + 	"\t\textracts a sub-model from file1 and writes BioPAX to output\n"
                +   "--help" +								"\t\t\t\t\t\tprints this screen and exits"
            );

        System.exit(-1);
    }

    private static Model getModel(BioPAXIOHandler io,
                                 String fName) throws FileNotFoundException {
        FileInputStream file = new FileInputStream(fName);
        return io.convertFromOWL(file);
    }
}
