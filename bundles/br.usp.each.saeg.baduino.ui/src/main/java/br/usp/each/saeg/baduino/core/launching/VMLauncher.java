package br.usp.each.saeg.baduino.core.launching;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_CLASSPATH;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.hamcrest.SelfDescribing;
import org.junit.runner.JUnitCore;

import br.usp.each.saeg.badua.agent.rt.internal.PreMain;
import br.usp.each.saeg.badua.commons.time.TimeWatch;
import br.usp.each.saeg.baduino.core.model.ProjectModel;
import br.usp.each.saeg.baduino.core.runnable.WatchFolder;
import br.usp.each.saeg.baduino.core.runner.BaduinoRunner;

/**
 * This class creates and launches a new VM.
 * @author Mario Concilio
 *
 */
public class VMLauncher {
	
	private static final Logger logger = Logger.getLogger(VMLauncher.class);

	private final IJavaProject javaProject;
	private final ProjectModel model;
	private final ILaunchManager manager;
	private final ILaunchConfigurationWorkingCopy workingCopy;
	private final List<String> classpath;
	private final List<VMListener> listeners;
	
	public VMLauncher(final ProjectModel model) throws CoreException, URISyntaxException {
		this.javaProject = model.getJavaProject();
		this.model = model;
		this.classpath = new ArrayList<>();
		this.manager = DebugPlugin.getDefault().getLaunchManager();
		this.listeners = new ArrayList<>();
		
		final ILaunchConfigurationType type = manager.getLaunchConfigurationType(ID_JAVA_APPLICATION);
		workingCopy = type.newInstance(null, "Baduino");
		configClasspath();
	}
	
	public void launch() throws CoreException {
		logger.debug("Launching new VM");
		
		final File file = new File(model.getCoverageBinPath());
		new WatchFolder(file, listeners).start();
		
		final ILaunchConfiguration configuration = workingCopy.doSave();
		DebugUITools.launch(configuration, ILaunchManager.RUN_MODE);
	}
	
	public void addListener(VMListener listener) {
		this.listeners.add(listener);
	}
	
	private void configClasspath() throws CoreException, URISyntaxException {
		logger.debug("Configuring Classpath for new VM");
		
		// adding selected project
		final IRuntimeClasspathEntry projectCp = JavaRuntime.newDefaultProjectClasspathEntry(javaProject);
		projectCp.setClasspathProperty(IRuntimeClasspathEntry.PROJECT);
		classpath.add(projectCp.getMemento());
		
		// external libs
		final IClasspathEntry[] entries = javaProject.getResolvedClasspath(true);
		for (IClasspathEntry entry : entries) {
			if (entry != null) {
				IPath srcPath = entry.getSourceAttachmentPath();
				IRuntimeClasspathEntry dependenciesEntry = JavaRuntime.newArchiveRuntimeClasspathEntry(entry.getPath());
				dependenciesEntry.setSourceAttachmentPath(srcPath);
				dependenciesEntry.setSourceAttachmentRootPath(entry.getSourceAttachmentRootPath());
				dependenciesEntry.setClasspathProperty(IRuntimeClasspathEntry.ARCHIVE);
				classpath.add(dependenciesEntry.getMemento());
			}
		}
		
		// junit
		final String junit = mementoForClass(JUnitCore.class);
		classpath.add(junit);

		// hamcrest
		final String hamcrest = mementoForClass(SelfDescribing.class);
		classpath.add(hamcrest);
		
		// log4j
		final String log4j = mementoForClass(Logger.class);
		classpath.add(log4j);

		// eclipse core
		final String eclipseCore = mementoForClass(IPath.class);
		classpath.add(eclipseCore);
		
		// eclipse jdt
		final String jdt = mementoForClass(IJavaProject.class);
		classpath.add(jdt);

		// ba-dua
		final String badua = mementoForClass(PreMain.class);
		classpath.add(badua);

		// baduino
//		final String path = "/Users/666mario/Documents/develop/each/baduino/bundles/br.usp.each.saeg.baduino.ui/target/br.usp.each.saeg.baduino.ui-0.3.23.jar";
//		final IPath ipath = new Path(path);
//		final IRuntimeClasspathEntry entry = JavaRuntime.newArchiveRuntimeClasspathEntry(ipath);
//		entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
//		String baduino = entry.getMemento();

		final String baduino = mementoForClass(BaduinoRunner.class);
		classpath.add(baduino);
		
		// saeg commons
		final String saeg = mementoForClass(TimeWatch.class);
		classpath.add(saeg);
		
//		final String javaLang = mementoForClass(MethodHandle.class);
//		classpath.add(javaLang);
		
		// setting classpath
		workingCopy.setAttribute(ATTR_CLASSPATH, classpath);
		workingCopy.setAttribute(ATTR_DEFAULT_CLASSPATH, false);
		
		// main class for new VM to execute
		workingCopy.setAttribute(ATTR_MAIN_TYPE_NAME, BaduinoRunner.class.getName());
		
		// setting up java agent from ba-dua
		final String arguments = new StringBuilder("-javaagent:")
				.append("\"")
				.append(pathForClass(PreMain.class))
				.append("\"")
				.toString();
		logger.debug("VM Arguments: " + arguments);
		workingCopy.setAttribute(ATTR_VM_ARGUMENTS, arguments);
		
		// passing xml path as parameter
		// so the new VM knows where to look for the xml file
		workingCopy.setAttribute(ATTR_PROGRAM_ARGUMENTS, model.getXmlPath());
	}
	
	private String mementoForClass(Class<?> clazz) throws CoreException, URISyntaxException {
		final String path = pathForClass(clazz);
		final IPath ipath = new Path(path);
		final IRuntimeClasspathEntry entry = JavaRuntime.newArchiveRuntimeClasspathEntry(ipath);
		entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
		
		return entry.getMemento();
	}
	
	private String pathForClass(Class<?> clazz) throws URISyntaxException {
		String path = clazz.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		if (isWindows()) {
			path = path.substring(1);
		}
		
		logger.debug(String.format("Path for %s: %s", clazz.getName(), path));
		return path;
	}
	
	private boolean isWindows() {
		final String os = System.getProperty("os.name").toLowerCase();
		return (os.indexOf("win") >= 0);
	}
	
}
