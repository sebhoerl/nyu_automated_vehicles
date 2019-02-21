package edu.nyu.matsim.automated_vehicles.operating_area;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.controler.AbstractModule;

import com.google.inject.Key;
import com.google.inject.name.Names;

import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.framework.AVUtils;

public class OperatingAreaModule extends AbstractModule {
	private final Network avNetwork;

	public OperatingAreaModule(Network avNetwork) {
		this.avNetwork = avNetwork;
	}

	@Override
	public void install() {
		AVUtils.registerGeneratorFactory(binder(), "OperatingArea", OperatingAreaVehicleGenerator.Factory.class);
		bind(Key.get(Network.class, Names.named(AVModule.AV_MODE))).toInstance(avNetwork);
	}
}
