package mil.nga.giat.geowave.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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

	private final static Logger LOGGER = Logger.getLogger(BigtableStoreTestEnvironment.class);

	private BigtableStoreTestEnvironment() {}

	@Override
	protected void initOptions(
			final StoreFactoryOptions options ) {
		HBaseOptions hbaseOptions = ((HBaseRequiredOptions) options).getAdditionalOptions();
		hbaseOptions.setBigtable(true);
		hbaseOptions.setEnableCustomFilters(false);
		hbaseOptions.setEnableCoprocessors(false);
	}

	@Override
	protected GenericStoreFactory<DataStore> getDataStoreFactory() {
		return STORE_FACTORY;
	}

	@Override
	public void setup() {
		ProcessBuilder pb = new ProcessBuilder(
				"gcloud-init.sh");

		String processDir = System.getProperty("user.dir");
		LOGGER.warn("Running gcloud install in " + processDir);	

		try {
			Process p = pb.start();
			p.waitFor();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		pb.command("source", "exportBigtableEnv");
		try {
			Process p = pb.start();
			p.waitFor();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
