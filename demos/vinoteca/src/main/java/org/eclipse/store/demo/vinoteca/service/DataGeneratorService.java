package org.eclipse.store.demo.vinoteca.service;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jakarta.annotation.PostConstruct;
import net.datafaker.Faker;
import org.eclipse.store.demo.vinoteca.dto.DataMetrics;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.GrapeVariety;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderItem;
import org.eclipse.store.demo.vinoteca.model.OrderStatus;
import org.eclipse.store.demo.vinoteca.model.Review;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.model.WineType;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Synthetic-data generator that bootstraps the Vinoteca demo on first start and powers the
 * "Data Generator" view at runtime.
 * <p>
 * The generator is built around a curated list of real-world wine
 * {@link WineRegion regions} (Bordeaux, Burgundy, Tuscany, Napa Valley, Mendoza, …), each pinned
 * to a realistic latitude/longitude bounding box and a small whitelist of well-known winery names
 * and grape varieties. From that it produces {@link Winery wineries} with believable coordinates,
 * grape-appropriate {@link Wine wines} (vintage 1990–2025, 8–300 € prices, plausible alcohol
 * content per type), {@link Customer customers} via {@link Faker datafaker}, randomly composed
 * {@link Order orders} and {@link Review reviews} that drive the wine ratings.
 * <p>
 * The {@link Random} is seeded with a constant ({@code 42}) so generated datasets are reproducible
 * across restarts as long as the catalogue tables above stay the same.
 *
 * @see #init()
 * @see #generate(int)
 */
@Service
public class DataGeneratorService
{
	private static final Logger LOG = LoggerFactory.getLogger(DataGeneratorService.class);

	private final DataRoot               dataRoot;
	private final EmbeddedStorageManager storageManager;
	private final Faker                  faker  = new Faker();
	private final Random                 random = new Random(42);

	private static final WineRegion[] REGIONS = {
		new WineRegion("Bordeaux",        "France",        44.84, -0.58,  44.3, 45.3, -1.2, 0.1),
		new WineRegion("Burgundy",        "France",        47.05,  4.38,  46.7, 47.4,  4.0, 5.0),
		new WineRegion("Champagne",       "France",        49.05,  3.95,  48.8, 49.3,  3.5, 4.4),
		new WineRegion("Rhone Valley",    "France",        44.13,  4.81,  43.6, 45.5,  4.3, 5.3),
		new WineRegion("Loire Valley",    "France",        47.39,  0.69,  47.0, 47.8, -0.5, 1.5),
		new WineRegion("Tuscany",         "Italy",         43.35, 11.35,  42.8, 43.9, 10.5, 12.2),
		new WineRegion("Piedmont",        "Italy",         44.70,  8.03,  44.3, 45.1,  7.5, 8.5),
		new WineRegion("Veneto",          "Italy",         45.44, 12.32,  45.0, 46.0, 11.5, 13.0),
		new WineRegion("Rioja",           "Spain",         42.46, -2.45,  42.1, 42.8, -3.0, -1.9),
		new WineRegion("Ribera del Duero","Spain",         41.65, -3.70,  41.3, 42.0, -4.2, -3.2),
		new WineRegion("Napa Valley",     "United States", 38.50,-122.35,  38.2, 38.8,-122.6,-122.1),
		new WineRegion("Sonoma County",   "United States", 38.48,-122.72,  38.2, 38.8,-123.0,-122.4),
		new WineRegion("Willamette Valley","United States", 45.12,-123.10,  44.8, 45.4,-123.4,-122.8),
		new WineRegion("Mendoza",         "Argentina",    -33.00,-68.50, -33.8,-32.2,-69.5,-67.5),
		new WineRegion("Barossa Valley",  "Australia",    -34.56, 138.95, -34.8,-34.3, 138.7, 139.2),
		new WineRegion("Margaret River",  "Australia",    -33.95, 115.05, -34.2,-33.7, 114.8, 115.3),
		new WineRegion("Stellenbosch",    "South Africa", -33.93,  18.86, -34.1,-33.7,  18.6,  19.1),
		new WineRegion("Marlborough",     "New Zealand",  -41.52, 173.95, -41.7,-41.3, 173.7, 174.2),
		new WineRegion("Mosel",           "Germany",       49.96,  6.98,  49.5, 50.4,  6.5,  7.5),
		new WineRegion("Rheingau",        "Germany",       50.02,  8.05,  49.8, 50.2,  7.8,  8.3),
		new WineRegion("Douro Valley",    "Portugal",      41.16, -7.79,  40.9, 41.4, -8.2, -7.4),
		new WineRegion("Alentejo",        "Portugal",      38.57, -7.91,  38.0, 39.1, -8.3, -7.5),
		new WineRegion("Tokaj",           "Hungary",       48.12, 21.41,  47.9, 48.3, 21.1, 21.7),
		new WineRegion("Wachau",          "Austria",       48.37, 15.43,  48.2, 48.5, 15.2, 15.7),
		new WineRegion("Central Otago",   "New Zealand",  -45.03, 169.20, -45.3,-44.7, 168.9, 169.5),
	};

	private static final String[][] WINERY_NAMES = {
		{"Chateau Margaux", "Chateau Lafite Rothschild", "Chateau Mouton Rothschild", "Chateau Haut-Brion"},
		{"Domaine de la Romanee-Conti", "Domaine Leroy", "Maison Louis Jadot"},
		{"Dom Perignon", "Krug", "Veuve Clicquot"},
		{"E. Guigal", "Chapoutier", "Paul Jaboulet Aine"},
		{"Domaine Huet", "Chateau de Chambord"},
		{"Antinori", "Frescobaldi", "Castello Banfi", "Sassicaia"},
		{"Gaja", "Giacomo Conterno", "Bruno Giacosa"},
		{"Allegrini", "Zenato", "Masi"},
		{"Marques de Riscal", "La Rioja Alta", "Muga"},
		{"Vega Sicilia", "Pesquera", "Pingus"},
		{"Robert Mondavi", "Opus One", "Stag's Leap Wine Cellars", "Caymus"},
		{"Kistler", "Williams Selyem", "Flowers"},
		{"Domaine Drouhin", "Eyrie Vineyards"},
		{"Catena Zapata", "Achaval Ferrer", "Zuccardi"},
		{"Penfolds", "Henschke", "Torbreck"},
		{"Leeuwin Estate", "Vasse Felix"},
		{"Kanonkop", "Meerlust", "Rustenberg"},
		{"Cloudy Bay", "Villa Maria"},
		{"Dr. Loosen", "Joh. Jos. Prum"},
		{"Schloss Johannisberg", "Robert Weil"},
		{"Quinta do Noval", "Taylor's"},
		{"Herdade do Esporao", "Monte da Ravasqueira"},
		{"Royal Tokaji", "Disznoko"},
		{"Domane Wachau", "F.X. Pichler"},
		{"Felton Road", "Rippon"},
	};

	private static final GrapeVariety[][] REGION_GRAPES = {
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.MERLOT},
		{GrapeVariety.PINOT_NOIR, GrapeVariety.CHARDONNAY},
		{GrapeVariety.CHARDONNAY, GrapeVariety.PINOT_NOIR},
		{GrapeVariety.SYRAH, GrapeVariety.GRENACHE},
		{GrapeVariety.SAUVIGNON_BLANC, GrapeVariety.CHARDONNAY},
		{GrapeVariety.SANGIOVESE, GrapeVariety.CABERNET_SAUVIGNON},
		{GrapeVariety.NEBBIOLO, GrapeVariety.MERLOT},
		{GrapeVariety.PINOT_GRIGIO, GrapeVariety.MERLOT},
		{GrapeVariety.TEMPRANILLO, GrapeVariety.GRENACHE},
		{GrapeVariety.TEMPRANILLO, GrapeVariety.CABERNET_SAUVIGNON},
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.CHARDONNAY, GrapeVariety.ZINFANDEL},
		{GrapeVariety.PINOT_NOIR, GrapeVariety.CHARDONNAY},
		{GrapeVariety.PINOT_NOIR, GrapeVariety.CHARDONNAY},
		{GrapeVariety.MALBEC, GrapeVariety.CABERNET_SAUVIGNON},
		{GrapeVariety.SYRAH, GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.GRENACHE},
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.CHARDONNAY},
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.PINOT_NOIR, GrapeVariety.SYRAH},
		{GrapeVariety.SAUVIGNON_BLANC, GrapeVariety.PINOT_NOIR},
		{GrapeVariety.RIESLING, GrapeVariety.PINOT_NOIR},
		{GrapeVariety.RIESLING, GrapeVariety.PINOT_NOIR},
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.SYRAH},
		{GrapeVariety.CABERNET_SAUVIGNON, GrapeVariety.SYRAH},
		{GrapeVariety.GEWURZTRAMINER, GrapeVariety.RIESLING},
		{GrapeVariety.RIESLING, GrapeVariety.GEWURZTRAMINER},
		{GrapeVariety.PINOT_NOIR, GrapeVariety.RIESLING},
	};

	private static final String[][] TASTING_NOTES = {
		{"blackcurrant, cedar, tobacco, plum, vanilla, leather"},
		{"cherry, raspberry, earth, mushroom, spice, violet"},
		{"apple, pear, butter, vanilla, citrus, honey"},
		{"lime, grapefruit, grass, gooseberry, mineral"},
		{"peach, apricot, petrol, honey, floral, mineral"},
		{"dark fruit, pepper, smoke, olive, chocolate, licorice"},
		{"cherry, plum, strawberry, rose, leather, spice"},
		{"dark cherry, tar, rose, truffle, chocolate, tobacco"},
		{"lemon, almond, peach, mineral, pear, ginger"},
		{"lychee, rose, ginger, honey, spice, tropical"},
		{"blackberry, pepper, plum, jam, chocolate, vanilla"},
		{"cherry, cranberry, raspberry, spice, earth, oak"},
	};

	private static final String[][] AROMAS = {
		{"blackcurrant", "cedar", "tobacco"},
		{"cherry", "raspberry", "earth"},
		{"apple", "pear", "butter"},
		{"lime", "grapefruit", "grass"},
		{"peach", "apricot", "petrol"},
		{"dark fruit", "pepper", "smoke"},
		{"cherry", "plum", "strawberry"},
		{"dark cherry", "tar", "rose"},
		{"lemon", "almond", "peach"},
		{"lychee", "rose", "ginger"},
		{"blackberry", "pepper", "plum"},
		{"cherry", "cranberry", "raspberry"},
	};

	private static final String[][] FOOD_PAIRINGS = {
		{"grilled steak", "lamb chops", "aged cheese"},
		{"roast duck", "mushroom risotto", "brie"},
		{"lobster", "creamy pasta", "roast chicken"},
		{"goat cheese salad", "shellfish", "sushi"},
		{"spicy Thai", "foie gras", "blue cheese"},
		{"grilled vegetables", "pizza", "charcuterie"},
		{"wild boar", "truffle pasta", "hard cheese"},
		{"osso buco", "braised beef", "polenta"},
		{"seafood paella", "tapas", "grilled fish"},
		{"dark chocolate", "berry desserts", "soft cheese"},
	};

	private static final String[] RED_BODY = {
		"full-bodied", "medium-bodied", "robust", "velvety", "silky",
		"rich", "bold", "structured", "muscular", "round"
	};

	private static final String[] WHITE_BODY = {
		"crisp", "light-bodied", "refreshing", "bright", "elegant",
		"delicate", "mineral-driven", "lean", "zesty", "vibrant"
	};

	private static final String[] POSITIVE_FINISH = {
		"long, lingering finish", "persistent, elegant finish",
		"smooth, velvety finish", "complex, layered finish",
		"beautifully balanced finish", "opulent, satisfying finish"
	};

	private static final String[] NEUTRAL_FINISH = {
		"moderate finish", "clean finish", "straightforward finish",
		"adequate finish", "simple but pleasant finish"
	};

	private static final String[] NEGATIVE_FINISH = {
		"short, abrupt finish", "thin finish", "harsh finish",
		"flat, forgettable finish", "somewhat bitter finish"
	};

	private static final String[] WINEMAKING_STYLES = {
		"Oak-aged", "Barrel-fermented", "Estate-bottled", "Small-batch",
		"Hand-harvested", "Old-vine", "Single-vineyard", "Reserve",
		"Late-harvest", "Classically styled", "Traditionally crafted"
	};

	/**
	 * @param dataRoot       the persistent root that will receive the generated entities
	 * @param storageManager the EclipseStore storage manager, used to {@code store} the customers
	 *                       and orders lists after generation
	 */
	public DataGeneratorService(final DataRoot dataRoot, final EmbeddedStorageManager storageManager)
	{
		this.dataRoot       = dataRoot;
		this.storageManager = storageManager;
	}

	/**
	 * Spring lifecycle hook invoked after the bean has been constructed. If the persistent graph
	 * is empty, generates an initial dataset of 300 wines (plus the related wineries, customers,
	 * orders and reviews); otherwise just logs the existing dataset size.
	 */
	@PostConstruct
	public void init()
	{
		if (!this.dataRoot.getWines().isEmpty())
		{
			LOG.info(
				"Data already loaded: {} wines, {} wineries, {} customers, {} orders.",
				this.dataRoot.getWines().size(),
				this.dataRoot.getWineries().size(),
				this.dataRoot.getCustomers().size(),
				this.dataRoot.getOrders().size()
			);
			return;
		}

		LOG.info("Generating initial demo data...");
		this.generate(300);
		LOG.info(
			"Generated: {} wines, {} wineries, {} customers, {} orders.",
			this.dataRoot.getWines().size(),
			this.dataRoot.getWineries().size(),
			this.dataRoot.getCustomers().size(),
			this.dataRoot.getOrders().size()
		);
	}

	/**
	 * Generates a complete dataset on top of whatever is already persisted: a fresh batch of
	 * wineries (one per region), {@code wineCount} wines distributed across them, 50 customers,
	 * 200 orders and reviews (at least 3 per wine). All generated data is appended to the existing graph and
	 * persisted before this method returns.
	 *
	 * @param wineCount the target number of wines to generate
	 * @return aggregate counts of the entire persisted graph (existing + newly generated)
	 */
	@Write
	public DataMetrics generate(final int wineCount)
	{
		final List<Winery>   wineries  = this.generateWineries();
		final List<Wine>     wines     = this.generateWines(wineries, wineCount);
		final List<Customer> customers = this.generateCustomers(50);
		final List<Order>    orders    = this.generateOrders(customers, wines, 200);

		this.generateReviews(customers, wines, wineCount * 5);

		// Persist all generated data
		this.dataRoot.getWines().store();
		this.dataRoot.getWineries().store();
		this.storageManager.storeAll(this.dataRoot.getCustomers(), this.dataRoot.getOrders());

		return new DataMetrics(
			this.dataRoot.getWineries().size(),
			this.dataRoot.getWines().size(),
			this.dataRoot.getCustomers().size(),
			this.dataRoot.getOrders().size(),
			wines.stream().mapToLong(w -> w.getReviews() != null ? w.getReviews().size() : 0).sum()
		);
	}

	private List<Winery> generateWineries()
	{
		final List<Winery> wineries = new ArrayList<>();
		for (int i = 0; i < REGIONS.length; i++)
		{
			final WineRegion region = REGIONS[i];
			final String[] names   = WINERY_NAMES[i];
			for (final String name : names)
			{
				final double lat = region.minLat + this.random.nextDouble() * (region.maxLat - region.minLat);
				final double lon = region.minLon + this.random.nextDouble() * (region.maxLon - region.minLon);
				final Winery winery = new Winery(
					name,
					region.name,
					region.country,
					Math.round(lat * 10000.0) / 10000.0,
					Math.round(lon * 10000.0) / 10000.0,
					"A prestigious winery in " + region.name + ", " + region.country,
					1800 + this.random.nextInt(224)
				);
				this.dataRoot.getWineries().add(winery);
				wineries.add(winery);
			}
		}
		return wineries;
	}

	private List<Wine> generateWines(final List<Winery> wineries, final int count)
	{
		final List<Wine> wines = new ArrayList<>();
		int generated = 0;
		while (generated < count)
		{
			for (int i = 0; i < REGIONS.length && generated < count; i++)
			{
				final int regionIdx = i;
				final GrapeVariety[] regionGrapes = REGION_GRAPES[regionIdx];
				final List<Winery> regionWineries = wineries.stream()
					.filter(w -> w.getRegion().equals(REGIONS[regionIdx].name))
					.toList();

				for (final Winery winery : regionWineries)
				{
					if (generated >= count)
					{
						break;
					}
					final int winesPerWinery = 2 + this.random.nextInt(5);
					for (int w = 0; w < winesPerWinery && generated < count; w++)
					{
						final GrapeVariety grape = regionGrapes[this.random.nextInt(regionGrapes.length)];
						final WineType type      = grapeToWineType(grape);
						final int vintage        = 1990 + this.random.nextInt(35);
						final double price       = 8.0 + this.random.nextDouble() * 292.0;
						final double rating      = Math.max(50, Math.min(100, 85 + this.random.nextGaussian() * 8));
						final int noteIdx        = this.random.nextInt(TASTING_NOTES.length);
						final int aromaIdx       = this.random.nextInt(AROMAS.length);
						final int pairingIdx     = this.random.nextInt(FOOD_PAIRINGS.length);
						final double alcohol     = type == WineType.RED
							? 12.0 + this.random.nextDouble() * 3.0
							: 9.0 + this.random.nextDouble() * 5.0;

						final String wineName = winery.getName() + " " +
							toTitleCase(grape.name()) + " " + vintage;

						final Wine wine = new Wine(
							wineName,
							winery,
							grape,
							type,
							vintage,
							Math.round(price * 100.0) / 100.0,
							Math.round(rating * 10.0) / 10.0,
							0,
							TASTING_NOTES[noteIdx][0],
							String.join(", ", AROMAS[aromaIdx]),
							String.join(", ", FOOD_PAIRINGS[pairingIdx]),
							Math.round(alcohol * 10.0) / 10.0,
							this.random.nextInt(500),
							true
						);

						this.dataRoot.getWines().add(wine);
						winery.getWines().add(wine);
						wines.add(wine);
						generated++;
					}
				}
			}
		}
		return wines;
	}

	private List<Customer> generateCustomers(final int count)
	{
		final List<Customer> customers = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			final String firstName = this.faker.name().firstName();
			final String lastName  = this.faker.name().lastName();
			final Customer customer = new Customer(
				firstName,
				lastName,
				this.faker.internet().emailAddress(firstName + " " + lastName),
				this.faker.address().city(),
				this.faker.address().country()
			);
			this.dataRoot.getCustomers().add(customer);
			customers.add(customer);
		}
		return customers;
	}

	private List<Order> generateOrders(
		final List<Customer> customers,
		final List<Wine>     wines,
		final int            count
	)
	{
		final List<Order>   orders   = new ArrayList<>();
		final OrderStatus[] statuses = OrderStatus.values();

		for (int i = 0; i < count; i++)
		{
			final Customer customer = customers.get(this.random.nextInt(customers.size()));
			final int itemCount     = 1 + this.random.nextInt(5);
			final List<OrderItem> items = new ArrayList<>();
			for (int j = 0; j < itemCount; j++)
			{
				final Wine wine = wines.get(this.random.nextInt(wines.size()));
				items.add(new OrderItem(
					wine,
					1 + this.random.nextInt(6),
					wine.getPrice()
				));
			}

			final LocalDateTime orderDate = LocalDateTime.of(
				2020 + this.random.nextInt(6),
				1 + this.random.nextInt(12),
				1 + this.random.nextInt(28),
				this.random.nextInt(24),
				this.random.nextInt(60)
			);

			final Order order = new Order(
				customer,
				orderDate,
				items,
				statuses[this.random.nextInt(statuses.length)]
			);
			this.dataRoot.getOrders().add(order);
			customer.getOrders().add(order);
			orders.add(order);
		}
		return orders;
	}

	private void generateReviews(
		final List<Customer> customers,
		final List<Wine>     wines,
		final int            count
	)
	{
		final int minPerWine = 3;

		// First, ensure every wine gets at least 3 reviews
		for (final Wine wine : wines)
		{
			for (int r = 0; r < minPerWine; r++)
			{
				this.addReview(customers, wine);
			}
		}

		// Distribute additional random reviews
		final int additional = Math.max(0, count - wines.size() * minPerWine);
		for (int i = 0; i < additional; i++)
		{
			final Wine wine = wines.get(this.random.nextInt(wines.size()));
			this.addReview(customers, wine);
		}
	}

	private void addReview(final List<Customer> customers, final Wine wine)
	{
		final Customer customer = customers.get(this.random.nextInt(customers.size()));
		final double   rating   = Math.max(1, Math.min(100, 80 + this.random.nextGaussian() * 12));

		final Review review = new Review(
			customer,
			Math.round(rating * 10.0) / 10.0,
			this.generateReviewText(wine, rating),
			LocalDateTime.of(
				2020 + this.random.nextInt(6),
				1 + this.random.nextInt(12),
				1 + this.random.nextInt(28),
				this.random.nextInt(24),
				this.random.nextInt(60)
			)
		);
		wine.getReviews().add(review);

		final double newAvg = wine.getReviews().stream()
			.mapToDouble(Review::getRating)
			.average()
			.orElse(0);
		wine.setRating(Math.round(newAvg * 10.0) / 10.0);
		wine.setRatingCount(wine.getReviews().size());
	}

	private String generateReviewText(final Wine wine, final double rating)
	{
		final String grape   = toTitleCase(wine.getGrapeVariety().name());
		final String region  = wine.getWinery().getRegion();
		final int    vintage = wine.getVintage();
		final String body    = wine.getType() == WineType.RED
			? pick(RED_BODY) : pick(WHITE_BODY);

		final String[] flavors = grapeFlavorDescriptors(wine.getGrapeVariety());
		final int idx1 = this.random.nextInt(flavors.length);
		int idx2 = this.random.nextInt(flavors.length);
		if (flavors.length > 1)
		{
			while (idx2 == idx1)
			{
				idx2 = this.random.nextInt(flavors.length);
			}
		}
		final String f1 = flavors[idx1];
		final String f2 = flavors[idx2];

		if (rating >= 85)
		{
			final String finish = pick(POSITIVE_FINISH);
			final String style  = pick(WINEMAKING_STYLES);
			return pick(new String[]{
				capitalize(body) + " " + grape + " with " + f1 + " and " + f2 + ". "
					+ style + " with a " + finish + ". A standout from " + region + ".",
				"Luscious " + vintage + " " + grape + ", " + f1 + " on the nose. "
					+ capitalize(body) + " and expressive, " + finish + ". " + region + " at its finest.",
				style + " " + grape + " from " + region + ". "
					+ capitalize(f1) + " and " + f2 + ", beautifully " + body + ". " + capitalize(finish) + ".",
				"A stunning " + body + " " + grape + ", " + f1 + " layered with " + f2 + ". "
					+ style + " character, " + finish + ". Pure " + region + " elegance.",
				capitalize(body) + " and opulent. This " + vintage + " " + grape + " bursts with " + f1 + ". "
					+ "Polished " + f2 + " undertones and a " + finish + ".",
				"Gorgeous " + region + " " + grape + ". " + capitalize(f1) + " meets " + f2
					+ " in this " + body + " gem. " + style + ", " + finish + ".",
				capitalize(f1) + " and " + f2 + " define this " + body + " " + grape + " from " + region + ". "
					+ style + ". " + capitalize(finish) + ". Highly recommended.",
				"Sweet " + grape + " with " + f1 + " and " + f2 + " finish. "
					+ capitalize(body) + ", " + style.toLowerCase() + ". " + capitalize(finish) + ".",
			});
		}
		else if (rating >= 65)
		{
			final String finish = pick(NEUTRAL_FINISH);
			return pick(new String[]{
				"Decent " + body + " " + grape + ". Some " + f1 + " on the palate, " + finish + ". "
					+ "Fair " + region + " expression.",
				grape + " with some " + f1 + " and " + f2 + ". " + capitalize(body) + " but "
					+ finish + ". Straightforward " + region + " wine.",
				"An acceptable " + vintage + " " + grape + ". " + capitalize(f1) + " comes through, "
					+ "though the " + f2 + " is muted. " + capitalize(finish) + ".",
				"Middle-of-the-road " + region + " " + grape + ". " + capitalize(body) + " with glimpses of "
					+ f1 + ". " + capitalize(finish) + ".",
				capitalize(body) + " enough, but lacks complexity. Some " + f1 + " and " + f2 + ". "
					+ capitalize(finish) + ". Everyday " + grape + ".",
				"Drinkable " + grape + " from " + region + ". Faint " + f1 + ", " + body
					+ " texture. " + capitalize(finish) + ". Fair value.",
			});
		}
		else
		{
			final String finish = pick(NEGATIVE_FINISH);
			return pick(new String[]{
				"Thin " + grape + " lacking depth. Faint " + f1 + ", " + finish + ". "
					+ region + " can do better.",
				"Disappointing " + vintage + " " + grape + ". Expected " + f1 + " but got a "
					+ finish + ". Not up to " + region + " standards.",
				"Watered-down " + grape + " from " + region + ". Hardly any " + f1 + " or " + f2 + ". "
					+ capitalize(finish) + ".",
				"Flat and uninteresting. This " + grape + " shows none of the " + f1 + " character I expected. "
					+ capitalize(finish) + ". Pass.",
				"A letdown from " + region + ". Neither " + body + " nor flavorful. "
					+ capitalize(finish) + " with barely a trace of " + f1 + ".",
			});
		}
	}

	private String pick(final String[] options)
	{
		return options[this.random.nextInt(options.length)];
	}

	private static String capitalize(final String s)
	{
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String[] grapeFlavorDescriptors(final GrapeVariety grape)
	{
		return switch (grape)
		{
			case CABERNET_SAUVIGNON -> new String[]{
				"cassis", "graphite", "pencil shavings", "dark chocolate",
				"eucalyptus", "black olive", "cigar box", "espresso"
			};
			case MERLOT -> new String[]{
				"plush plum", "cocoa", "velvet cherry", "mocha",
				"fig", "soft spice", "toffee", "damson"
			};
			case PINOT_NOIR -> new String[]{
				"wild strawberry", "forest floor", "dried rose petal", "cola",
				"blood orange", "wet stone", "cranberry", "potpourri"
			};
			case SYRAH -> new String[]{
				"cracked pepper", "smoked meat", "blueberry compote", "garrigue",
				"iron", "violet ink", "bramble", "charred oak"
			};
			case TEMPRANILLO -> new String[]{
				"dried fig", "sweet tobacco", "dusty earth", "warm brick",
				"baking spice", "tanned leather", "dried cranberry", "tomato leaf"
			};
			case SANGIOVESE -> new String[]{
				"sour cherry", "dried oregano", "terracotta", "sun-dried tomato",
				"almond skin", "sage", "blood orange", "dried thyme"
			};
			case NEBBIOLO -> new String[]{
				"tar and roses", "dried cherry", "anise", "truffle",
				"autumn leaves", "camphor", "licorice root", "potpourri"
			};
			case MALBEC -> new String[]{
				"blackberry preserve", "dark plum", "milk chocolate", "grilled meat",
				"sweet spice", "violet", "mesquite", "cocoa powder"
			};
			case GRENACHE -> new String[]{
				"raspberry coulis", "white pepper", "dried lavender", "kirsch",
				"candied orange", "cinnamon", "garrigue", "strawberry jam"
			};
			case ZINFANDEL -> new String[]{
				"boysenberry", "black pepper", "cinnamon stick", "briar",
				"baked fruit", "brown sugar", "clove", "jammy fruit"
			};
			case CHARDONNAY -> new String[]{
				"golden apple", "brioche", "lemon curd", "toasted hazelnut",
				"beeswax", "white peach", "creme brulee", "butterscotch"
			};
			case SAUVIGNON_BLANC -> new String[]{
				"passion fruit", "cut grass", "green bell pepper", "chalky mineral",
				"white grapefruit", "elderflower", "flint", "nettles"
			};
			case RIESLING -> new String[]{
				"petrol", "lime zest", "white blossom", "wet slate",
				"quince", "jasmine", "green apple", "lanolin"
			};
			case PINOT_GRIGIO -> new String[]{
				"asian pear", "white almond", "sea breeze", "fresh linen",
				"green melon", "chalk", "white flowers", "wet stone"
			};
			case GEWURZTRAMINER -> new String[]{
				"turkish delight", "lychee", "rose water", "mango",
				"candied ginger", "star anise", "marmalade", "exotic spice"
			};
		};
	}

	/**
	 * Computes top-level entity counts of the persisted graph for the analytics dashboard.
	 *
	 * @return aggregate dataset metrics
	 */
	@Read
	public DataMetrics getMetrics()
	{
		final long reviewCount = this.dataRoot.getWines().query().stream()
			.mapToLong(w -> w.getReviews() != null ? w.getReviews().size() : 0)
			.sum();
		return new DataMetrics(
			this.dataRoot.getWineries().size(),
			this.dataRoot.getWines().size(),
			this.dataRoot.getCustomers().size(),
			this.dataRoot.getOrders().size(),
			reviewCount
		);
	}

	private static WineType grapeToWineType(final GrapeVariety grape)
	{
		return switch (grape)
		{
			case CABERNET_SAUVIGNON, MERLOT, PINOT_NOIR, SYRAH, TEMPRANILLO,
				 SANGIOVESE, NEBBIOLO, MALBEC, GRENACHE, ZINFANDEL -> WineType.RED;
			case CHARDONNAY, SAUVIGNON_BLANC, RIESLING, PINOT_GRIGIO, GEWURZTRAMINER -> WineType.WHITE;
		};
	}

	private static String toTitleCase(final String enumName)
	{
		final String[] parts = enumName.split("_");
		final StringBuilder sb = new StringBuilder();
		for (final String part : parts)
		{
			if (!sb.isEmpty())
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)))
			  .append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	/**
	 * Internal descriptor for a real-world wine region: its centroid (used for display) plus a
	 * latitude/longitude bounding box from which winery coordinates are randomly sampled.
	 *
	 * @param name    the region name (e.g. {@code "Bordeaux"})
	 * @param country the country the region belongs to
	 * @param lat     the centroid latitude
	 * @param lon     the centroid longitude
	 * @param minLat  the minimum sampling latitude
	 * @param maxLat  the maximum sampling latitude
	 * @param minLon  the minimum sampling longitude
	 * @param maxLon  the maximum sampling longitude
	 */
	private record WineRegion(
		String name, String country,
		double lat, double lon,
		double minLat, double maxLat,
		double minLon, double maxLon
	) {}
}
