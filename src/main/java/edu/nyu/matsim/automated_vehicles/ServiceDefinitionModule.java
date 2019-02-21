package edu.nyu.matsim.automated_vehicles;

import org.matsim.core.controler.AbstractModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;

import ch.ethz.matsim.av.config.AVConfig;
import ch.ethz.matsim.av.config.AVDispatcherConfig;
import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.config.AVOperatorConfig;
import ch.ethz.matsim.av.config.AVPriceStructureConfig;

public class ServiceDefinitionModule extends AbstractModule {
	private final long fleetSize;
	
	public ServiceDefinitionModule(long fleetSize) {
		this.fleetSize = fleetSize;
	}

	@Override
	public void install() {

	}

	@Provides
	@Singleton
	public AVConfig provideAVConfig() {
		AVConfig avConfig = new AVConfig();
		AVOperatorConfig operatorConfig = avConfig.createOperatorConfig("av");

		// Define generator, including fleet size
		AVGeneratorConfig generatorConfig = operatorConfig.createGeneratorConfig("OperatingArea");
		generatorConfig.setNumberOfVehicles(fleetSize);

		// Define which dispatcher to use
		AVDispatcherConfig dispatcherConfig = operatorConfig.createDispatcherConfig("SingleHeuristic");

		// Define the price structure
		AVPriceStructureConfig priceStructureConfig = operatorConfig.createPriceStructureConfig();

		return avConfig;
	}
}
