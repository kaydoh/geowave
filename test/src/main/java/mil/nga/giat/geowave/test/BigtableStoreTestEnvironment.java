package mil.nga.giat.geowave.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

import mil.nga.giat.geowave.core.store.DataStore;
import mil.nga.giat.geowave.core.store.GenericStoreFactory;
import mil.nga.giat.geowave.core.store.StoreFactoryOptions;
import mil.nga.giat.geowave.datastore.hbase.HBaseDataStoreFactory;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseOptions;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseRequiredOptions;

public class BigtableStoreTestEnvironment extends
		StoreTestEnvironment
{
	private static final GenericStoreFactory<DataStore> STORE_FACTORY = new HBaseDataStoreFactory();
	private static BigtableStoreTestEnvironment singletonInstance = null;

	public static synchronized BigtableStoreTestEnvironment getInstance() {
		if (singletonInstance == null) {
			singletonInstance = new BigtableStoreTestEnvironment();
		}
		return singletonInstance;
	}

	private final static Logger LOGGER = Logger.getLogger(
			BigtableStoreTestEnvironment.class);

	private BigtableStoreTestEnvironment() {}

	@Override
	protected void initOptions(
			final StoreFactoryOptions options ) {
		HBaseOptions hbaseOptions = ((HBaseRequiredOptions) options).getAdditionalOptions();
		hbaseOptions.setBigtable(
				true);
		hbaseOptions.setEnableCustomFilters(
				false);
		hbaseOptions.setEnableCoprocessors(
				false);
	}

	@Override
	protected GenericStoreFactory<DataStore> getDataStoreFactory() {
		return STORE_FACTORY;
	}

	@Override
	public void setup() {
		// Bigtable IT's rely on an external gcloud emulator
	}
	
	// Currently being run from travis externally
	private void initGcloud() {
		String processDir = System.getProperty(
				"user.dir");
		LOGGER.warn(
				"KAM >>> Running gcloud install in " + processDir);
				
		String chmodIt = "/bin/bash -c chmod 755 " + processDir + "/gcloud-init.sh";
		String chmodOut = executeCommand(chmodIt);
		LOGGER.warn(chmodOut);

		String cmdOut = executeCommand(processDir + "/gcloud-init.sh");
		LOGGER.warn(cmdOut);
		
		String sourceIt = "/bin/bash -c " + processDir + "/exportBigtableEnv";
		String srcOut = executeCommand(sourceIt);
		LOGGER.warn(cmdOut);
	}

	private String executeCommand(
			String command ) {
		StringBuffer output = new StringBuffer();

		Process p;
		try {
			p = Runtime.getRuntime().exec(
					command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(
							p.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(
						line + "\n");
			}

		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return output.toString();
	}

	@Override
	public void tearDown() {}

	@Override
	public TestEnvironment[] getDependentEnvironments() {
		return new TestEnvironment[] {
			ZookeeperTestEnvironment.getInstance()
		};
	}
}
