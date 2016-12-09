package mil.nga.giat.geowave.test;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Logger;

public class BigtableEmulator
{
	private final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BigtableEmulator.class);
	
	private final static String GCLOUD_URL = "https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/";
	private final static String GCLOUD_TAR = "google-cloud-sdk-136.0.0-linux-x86_64.tar.gz";
	private final static String GCLOUD_EXE = "google-cloud-sdk/bin/gcloud";
	private static final String HOST_PORT = "localhost:8128";

	private final File sdkDir;

	public BigtableEmulator(
			String sdkDir ) {
		if (TestUtils.isSet(
				sdkDir)) {
			this.sdkDir = new File(
					sdkDir);
		}
		else {
			this.sdkDir = new File(
					TestUtils.TEMP_DIR,
					"gcloud");
		}

		if (!this.sdkDir.exists() && !this.sdkDir.mkdirs()) {
			LOGGER.warn(
					"unable to create directory " + this.sdkDir.getAbsolutePath());
		}
	}

	public boolean start() {
		if (!isInstalled()) {
			try {
				if (!install()) {
					return false;
				}
			}
			catch (IOException e) {
				LOGGER.error(e.getMessage());
				return false;
			}
		}
		
		try {
			startEmulator();
		}
		catch (IOException | InterruptedException e) {
			LOGGER.error(e.getMessage());
			return false;
		}
		
		return true;
	}
	
	private boolean isInstalled() {
		final File gcloudExe = new File(
				sdkDir,
				GCLOUD_EXE);

		return (gcloudExe.canExecute());
	}

	protected boolean install()
			throws IOException {
		URL url = new URL(
				GCLOUD_URL+GCLOUD_TAR);

		final File downloadFile = new File(
				sdkDir,
				GCLOUD_TAR);
		if (!downloadFile.exists()) {
			try (FileOutputStream fos = new FileOutputStream(
					downloadFile)) {
				IOUtils.copyLarge(
						url.openStream(),
						fos);
				fos.flush();
			}
		}

		final TarGZipUnArchiver unarchiver = new TarGZipUnArchiver();
		unarchiver.enableLogging(
				new ConsoleLogger(
						Logger.WARN,
						"Gcloud SDK Unarchive"));
		unarchiver.setSourceFile(
				downloadFile);
		unarchiver.setDestDirectory(
				sdkDir);
		unarchiver.extract();
		
		if (!downloadFile.delete()) {
			LOGGER.warn(
					"cannot delete " + downloadFile.getAbsolutePath());
		}
		
		// Check the install
		if (!isInstalled()) {
			LOGGER.error("Gcloud install failed");
			return false;
		}
		
		// Install the beta components
		CommandLine cmdLine = new CommandLine(sdkDir+"/"+GCLOUD_EXE);
		cmdLine.addArgument("components");
		cmdLine.addArgument("install");
		cmdLine.addArgument("beta");
		cmdLine.addArgument("--quiet");
		DefaultExecutor executor = new DefaultExecutor();
		int exitValue = executor.execute(cmdLine);
		
		LOGGER.warn(
				"KAM >>> gcloud install beta; exit code = " + exitValue);

		// the emulator needs to be started interactively to complete the install
		//runScript();
		
		return true;
	}
	
	// Currently being run from travis externally
	private void runScript() {
		try {
			String testDir = System.getProperty(
					"user.dir");
			LOGGER.warn(
					"KAM >>> Running gcloud setup in " + testDir);

			// Ensure script is executable
			String scriptFilename = testDir + "/gcloud-init.sh";
			File scriptFile = new File(
					scriptFilename);
			if (!scriptFile.canExecute()) {
				scriptFile.setExecutable(true);
			}
			
			// Run the script using commons exec
			CommandLine cmdLine = new CommandLine(scriptFilename);
			cmdLine.addArgument(sdkDir.getAbsolutePath());
			
			DefaultExecutor executor = new DefaultExecutor();
			int exitValue = executor.execute(cmdLine);
			
			LOGGER.warn(
					"KAM >>> gcloud setup exit code = " + exitValue);
		}
		catch (IOException e) {
			LOGGER.error(
					e.getMessage());
		}
	}

	/**
	 * Using apache commons exec for cmd line execution
	 * 
	 * @param command
	 * @return exitCode
	 * @throws ExecuteException
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	private void startEmulator()
			throws ExecuteException,
			IOException, InterruptedException {
		CommandLine cmdLine = new CommandLine(sdkDir+"/"+GCLOUD_EXE);
		cmdLine.addArgument("beta");
		cmdLine.addArgument("emulators");
		cmdLine.addArgument("bigtable");
		cmdLine.addArgument("start");
		cmdLine.addArgument("--host-port");
		cmdLine.addArgument(HOST_PORT);
		
		DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

		ExecuteWatchdog watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
		Executor executor = new DefaultExecutor();
		executor.setWatchdog(watchdog);
		executor.execute(cmdLine, resultHandler);

		// some time later the result handler callback was invoked so we
		// can safely request the exit value
		
		resultHandler.waitFor();
	}
}
