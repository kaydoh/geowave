package mil.nga.giat.geowave.cli.geoserver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import mil.nga.giat.geowave.core.cli.annotations.GeowaveOperation;
import mil.nga.giat.geowave.core.cli.api.Command;
import mil.nga.giat.geowave.core.cli.api.OperationParams;
import mil.nga.giat.geowave.core.cli.operations.config.options.ConfigOptions;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@GeowaveOperation(name = "addds", parentOperation = GeoServerSection.class)
@Parameters(commandDescription = "Add a GeoServer datastore")
public class GeoServerAddStoreCommand implements
		Command
{
	private GeoServerRestClient geoserverClient = null;
	private HashMap<String, String> geowaveStoreConfig;

	@Parameter(names = {
		"-ws",
		"--workspace"
	}, required = true, description = "<workspace name>")
	private String workspace;

	@Parameter(description = "<datastore name>")
	private List<String> parameters = new ArrayList<String>();
	private String datastore = null;

	@Override
	public boolean prepare(
			OperationParams params ) {
		if (geoserverClient == null) {
			// Get the local config for GeoServer
			File propFile = (File) params.getContext().get(
					ConfigOptions.PROPERTIES_FILE_CONTEXT);

			GeoServerConfig config = new GeoServerConfig(
					propFile);

			geowaveStoreConfig = config.loadStoreConfig(
					propFile,
					datastore);

			// Create the rest client
			geoserverClient = new GeoServerRestClient(
					config);
		}

		// Successfully prepared
		return true;
	}

	@Override
	public void execute(
			OperationParams params )
			throws Exception {
		if (parameters.size() != 1) {
			throw new ParameterException(
					"Requires argument: <datastore name>");
		}

		datastore = parameters.get(0);
		
		Response addStoreResponse = geoserverClient.addDatastore(
				workspace,
				datastore,
				"accumulo",
				geowaveStoreConfig);

		if (addStoreResponse.getStatus() == Status.OK.getStatusCode() || addStoreResponse.getStatus() == Status.CREATED.getStatusCode()) {
			System.out.println("Add store '" + datastore + "' to workspace '" + workspace + "' on GeoServer: OK");
		}
		else {
			System.err.println("Error adding store '" + datastore + "' to workspace '" + workspace + "' on GeoServer; code = " + addStoreResponse.getStatus());
		}
	}
}
