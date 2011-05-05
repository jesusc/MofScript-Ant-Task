package modelum.mofscript.ant;


import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.mofscript.parser.MofScriptParseError;
import org.eclipse.mofscript.parser.ParserUtil;
import org.eclipse.mofscript.runtime.ExecutionManager;
import org.eclipse.mofscript.runtime.ExecutionMessageListener;
import org.eclipse.mofscript.runtime.MofScriptExecutionException;

public class MOFScriptTask extends Task implements ExecutionMessageListener {
	private String template = null;
	private String templatesDir = "";
	private String outputDir = "";

	LinkedList<Model> models = new LinkedList<Model>(); 

	public class Model {    
		protected String model;
		protected String pathMetamodel;
		protected String uri;

		public String getMetamodel() { 	return pathMetamodel;	}
		public String getModel() {	return model;	}
		public String getURI() {	return uri;	}

		public void setMetamodel(String pathMetamodel) { this.pathMetamodel = pathMetamodel; }
		public void setURI(String uri) { this.uri = uri;	}
		public void setModel(String model) { this.model = model; }        
	}

	public Model createModel() {                               
		Model par = new Model();
		models.add(par);
		return par;
	}

	private String getPath(String path) {
		System.out.println("path: " + path + " -> " + this.getProject().resolveFile(path).getPath());
		return this.getProject().resolveFile(path).getPath();
	}
	
	private void registerEPackages(Collection<EPackage> packages) {
		for (EPackage pkg : packages) {
			EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);	
			registerEPackages(pkg.getESubpackages());
			System.out.println("Registered metamodel: "+ pkg.getNsURI());
		}
	}
	
	public void execute() throws BuildException {
		if ( models.size() == 0 ) {
			throw new BuildException("No model defined");
		}
		if ( this.template == null ) {
			throw new BuildException("No template defined");
		}

		// Register factory extension
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());

		Iterator it = models.iterator();
		while(it.hasNext()) {
			Model p = (Model) it.next();

			// Read source metamodel
			ResourceSet rsSource = new ResourceSetImpl();
			Resource resSource = rsSource.getResource(URI.createFileURI(getPath(p.pathMetamodel)), true);
			
			LinkedList<EPackage> packages = new LinkedList<EPackage>();
			EList<EObject> objects = resSource.getContents();
			for (EObject object : objects) {
				if ( object instanceof EPackage )
					packages.add((EPackage) object);
			}
			
			registerEPackages(packages);
		}

		ParserUtil parserUtil = new ParserUtil();
		parserUtil.setMetaModelRepositoryURI(getPath(templatesDir)); 
		parserUtil.setCompilePath(getPath(templatesDir)); 
		parserUtil.parse(new File(getPath(templatesDir), template), true);
		
		// Check errors
		int errorCount = ParserUtil.getModelChecker().getErrorCount();
		Iterator errorIt = ParserUtil.getModelChecker().getErrors(); // Iterator of MofScriptParseError objects
		System.out.println ("Parsing result: " + errorCount + " errors");
		if (errorCount > 0) {
			for (;errorIt.hasNext();) {
				MofScriptParseError parseError = (MofScriptParseError) errorIt.next();
				System.out.println("\t \t: Error: " + parseError.toString());
			}
			return;
		}		
				
		ExecutionManager execMgr = ExecutionManager.getExecutionManager();
		
		XMIResourceFactoryImpl xmiFactory = null;
		EObject sourceModel = null;
		File sourceModelFile = null;
		ResourceSet rSet = null;
		it = models.iterator();
		while(it.hasNext()) {
			Model p = (Model) it.next();
			
			// Load source models
			xmiFactory = new XMIResourceFactoryImpl();
			sourceModel = null;
			sourceModelFile = new File(getPath(p.model));
			rSet = new ResourceSetImpl ();
			rSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", xmiFactory);
			URI uri = URI.createFileURI(sourceModelFile.getAbsolutePath());
			Resource resource = rSet.getResource(uri, true);
			if (resource != null) {
				if (resource.getContents().size() > 0) {
					sourceModel = (EObject) resource.getContents().get(0);
				}
			}
			// set the source model for the execution manager
			execMgr.addSourceModel(sourceModel); 
		}
		// sets the root output directory, if any is desired (e.g. "c:/temp")
		execMgr.setRootDirectory(getPath(outputDir)); 
		// if true, files are not generated to the file systsm, but populated into a filemodel
		// which can be fetched afterwards. Value false will result in standard file generation
		execMgr.setUseFileModel(false);
		// Turns on/off system logging
		execMgr.setUseLog(false);
		// Adds an output listener for the transformation execution.
		execMgr.getExecutionStack().addOutputMessageListener(this);
		try {
			execMgr.executeTransformation();
		} catch (MofScriptExecutionException mex) {
			mex.printStackTrace();
		}
	}


	/**
	 * ExecutionMessageListener interface operations
	 */
	public void executionMessage (String type, String description) {
		System.out.println (type + " - " + description);
	}

	// The setter for the "template" attribute
	public void setTemplate(String template) {
		this.template = template;
	}
	
	// The setter for the "templatesDir" attribute
	public void setTemplatesDir(String templatesDir) {
		this.templatesDir = templatesDir;
	}

	// The setter for the "outputDir" attribute
	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}

}
