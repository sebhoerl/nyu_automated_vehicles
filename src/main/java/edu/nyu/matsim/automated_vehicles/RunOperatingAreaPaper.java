package edu.nyu.matsim.automated_vehicles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;

import ch.ethz.matsim.av.framework.AVConfigGroup;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.baseline_scenario.config.CommandLine;
import ch.ethz.matsim.baseline_scenario.config.CommandLine.ConfigurationException;
import ch.ethz.matsim.discrete_mode_choice.modules.ConstraintModule;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceConfigurator;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import ch.ethz.matsim.discrete_mode_choice.modules.ModelModule.ModelType;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.LinkAttributeConstraintConfigGroup;
import edu.nyu.matsim.automated_vehicles.operating_area.OperatingAreaModule;
import edu.nyu.matsim.automated_vehicles.operating_area.ShpFilter;

public class RunOperatingAreaPaper {
	static public void main(String[] args) throws UncheckedIOException, ConfigurationException, IOException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path", "operating-area-path", "fleet-size") //
				.build();

		// Load configuration
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), new DvrpConfigGroup(),
				new AVConfigGroup());
		adaptConfiguration(config);

		// Load scenario
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network avNetwork = createAVNetwork(scenario.getNetwork(),
				new File(cmd.getOptionStrict("operating-area-path")));

		// Set up controller
		Controler controller = new Controler(scenario);
		controller.addOverridingModule(new DiscreteModeChoiceModule());
		controller.addOverridingModule(new DvrpTravelTimeModule());
		controller.addOverridingModule(new AVModule()); // adds AVs
		controller.addOverridingModule(new OperatingAreaModule(avNetwork)); // defines operating area

		long fleetSize = Long.parseLong(cmd.getOptionStrict("fleet-size"));
		controller.addOverridingModule(new ServiceDefinitionModule(fleetSize)); // defines the AV service

		controller.run();
	}

	static private void adaptConfiguration(Config config) {
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		// Add "av" mode
		List<String> modes = new ArrayList<>(Arrays.asList(config.subtourModeChoice().getModes()));
		modes.add(AVModule.AV_MODE);
		config.subtourModeChoice().setModes(modes.toArray(new String[] {}));

		// Add scoring for "av" mode
		ModeParams scoringParams = config.planCalcScore().getOrCreateModeParams("av");
		scoringParams.setConstant(0.0);
		scoringParams.setMarginalUtilityOfTraveling(0.0);

		// Configure discrete mode choice
		DiscreteModeChoiceConfigurator.configureAsSubtourModeChoiceReplacement(config);
		DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
				.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);

		dmcConfig.setTourConstraints(
				Arrays.asList(ConstraintModule.VEHICLE_CONTINUITY, ConstraintModule.FROM_TRIP_BASED));
		dmcConfig.setTripConstraints(Arrays.asList(ConstraintModule.LINK_ATTRIBUTE));
		dmcConfig.setModelType(ModelType.Tour);

		// Set up the operating area constraint for the users
		LinkAttributeConstraintConfigGroup linkAttributeConfig = dmcConfig.getLinkAttributeConstraintConfigGroup();
		linkAttributeConfig.setAttributeName("avOperatingArea");
		linkAttributeConfig.setAttributeValue("true");
		linkAttributeConfig.setConstrainedModes(Arrays.asList(AVModule.AV_MODE));
	}

	/**
	 * Takes the original network and extracts a restricted network for AVs (and the
	 * operating area).
	 */
	static private Network createAVNetwork(Network network, File shapefilePath) throws IOException {
		// Create a network only with car links
		Network avNetwork = NetworkUtils.createNetwork();
		TransportModeNetworkFilter carFilter = new TransportModeNetworkFilter(network);
		carFilter.filter(avNetwork, Collections.singleton(TransportMode.car));

		// Find all link IDs in the operating area
		ShpFilter shpFilter = ShpFilter.create(shapefilePath);
		Collection<Id<Link>> validLinkIds = shpFilter.filter(avNetwork);

		// Find all link IDs that have to be removed and remove them
		Set<Id<Link>> removal = new HashSet<>(avNetwork.getLinks().keySet());
		removal.removeAll(validLinkIds);
		removal.forEach(avNetwork::removeLink);

		// Clean the new network
		new NetworkCleaner().run(avNetwork);

		// Set the av mode + an attribute on all links of the original network
		for (Id<Link> linkId : avNetwork.getLinks().keySet()) {
			Link link = network.getLinks().get(linkId);

			Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
			allowedModes.add(AVModule.AV_MODE);
			link.setAllowedModes(allowedModes);

			link.getAttributes().putAttribute("avOperatingArea", "true");
		}

		return avNetwork;
	}
}
