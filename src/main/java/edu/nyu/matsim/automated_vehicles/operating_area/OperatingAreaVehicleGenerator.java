package edu.nyu.matsim.automated_vehicles.operating_area;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.dvrp.data.Vehicle;
import org.matsim.core.gbl.MatsimRandom;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import ch.ethz.matsim.av.config.AVGeneratorConfig;
import ch.ethz.matsim.av.data.AVVehicle;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.generator.AVGenerator;

public class OperatingAreaVehicleGenerator implements AVGenerator {
	private final List<Id<Link>> linkIds;
	private final List<Double> cdf;
	private final int fleetSize;
	private final Random random;
	private final Network network;
	private int count = 0;

	public OperatingAreaVehicleGenerator(List<Id<Link>> linkIds, List<Double> cdf, int fleetSize, Random random,
			Network network) {
		this.linkIds = linkIds;
		this.cdf = cdf;
		this.fleetSize = fleetSize;
		this.random = random;
		this.network = network;
	}

	@Override
	public boolean hasNext() {
		return count < fleetSize;
	}

	@Override
	public AVVehicle next() {
		count++;

		double selector = random.nextDouble();
		int selectorIndex = 0;

		while (cdf.get(selectorIndex) < selector) {
			selectorIndex++;
		}

		Id<Vehicle> vehicleId = Id.create(String.format("av_%d", count), Vehicle.class);

		Id<Link> startLinkId = linkIds.get(selectorIndex);
		Link startLink = network.getLinks().get(startLinkId);

		return new AVVehicle(vehicleId, startLink, 1.0, 0.0, Double.POSITIVE_INFINITY);
	}

	@Singleton
	public static class Factory implements AVGeneratorFactory {
		private final List<Id<Link>> linkIds;
		private final List<Double> cdf;
		private final Network network;

		@Inject
		public Factory(Population population, @Named(AVModule.AV_MODE) Network network) {
			this.network = network;
			linkIds = new ArrayList<>(network.getLinks().keySet());

			List<Double> weights = new ArrayList<>(linkIds.size());
			List<Id<Link>> listedLinkIds = new ArrayList<>(linkIds.size());

			for (Id<Link> linkId : linkIds) {
				listedLinkIds.add(linkId);
				weights.add(0.0);
			}

			for (Person person : population.getPersons().values()) {
				Plan plan = person.getSelectedPlan();

				if (plan.getPlanElements().size() > 0) {
					Activity firstActivity = (Activity) plan.getPlanElements().get(0);
					int linkIndex = listedLinkIds.indexOf(firstActivity.getLinkId());

					if (linkIndex > -1) {
						weights.set(linkIndex, weights.get(linkIndex) + 1);
					}
				}
			}

			cdf = new ArrayList<>(linkIds.size());

			for (int i = 0; i < linkIds.size(); i++) {
				if (i == 0) {
					cdf.add(weights.get(0));
				} else {
					cdf.add(cdf.get(i - 1) + weights.get(i));
				}
			}

			for (int i = 0; i < linkIds.size(); i++) {
				cdf.set(i, cdf.get(i) / cdf.get(linkIds.size() - 1));
			}
		}

		@Override
		public AVGenerator createGenerator(AVGeneratorConfig generatorConfig) {
			Random random = MatsimRandom.getLocalInstance();
			return new OperatingAreaVehicleGenerator(linkIds, cdf, (int) generatorConfig.getNumberOfVehicles(), random,
					network);
		}

	}
}
