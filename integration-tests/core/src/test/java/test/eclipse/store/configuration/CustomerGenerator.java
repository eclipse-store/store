package test.eclipse.store.configuration;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CustomerGenerator
{

    private static final Random RANDOM = new Random();

    private static final String[] FIRST_NAMES = {
            "John", "Jane", "Michael", "Sarah", "David", "Emma", "James", "Olivia",
            "Robert", "Emily", "William", "Sophia", "Richard", "Isabella", "Joseph",
            "Ava", "Thomas", "Mia", "Charles", "Charlotte", "Daniel", "Amelia",
            "Matthew", "Harper", "Anthony", "Evelyn", "Mark", "Abigail", "Donald",
            "Elizabeth", "Paul", "Sofia", "Steven", "Avery", "Andrew", "Ella",
            "Joshua", "Scarlett", "Kenneth", "Grace", "Kevin", "Chloe", "Brian",
            "Victoria", "George", "Riley", "Edward", "Aria", "Ronald", "Lily"
    };

    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
            "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez",
            "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
            "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark",
            "Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King",
            "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores", "Green",
            "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
            "Carter", "Roberts"
    };

    private static final String[] STREET_NAMES = {
            "Main Street", "High Street", "Park Avenue", "Oak Avenue", "Maple Street",
            "Washington Street", "Elm Street", "Lake Street", "Hill Road", "Church Street",
            "Spring Street", "Cedar Lane", "Pine Street", "River Road", "Market Street",
            "Broadway", "Second Street", "Third Street", "Fourth Street", "Fifth Street",
            "Sunset Boulevard", "College Street", "School Street", "Forest Drive",
            "Franklin Street", "Mill Road", "Lincoln Avenue", "Madison Avenue",
            "Jefferson Street", "Center Street", "Union Street", "Park Street",
            "North Street", "South Street", "West Street", "East Street", "Chestnut Street",
            "Walnut Street", "Bridge Street", "Cherry Street", "Willow Street",
            "Maple Avenue", "Oak Drive", "Valley Road", "Highland Avenue", "Grove Street",
            "Summit Avenue", "Pleasant Street", "Cambridge Road", "Oxford Street"
    };

    private static final String[] STREET_NUMBERS = {
            "1", "2", "5", "10", "12", "15", "18", "20", "23", "25", "28", "30",
            "33", "35", "38", "40", "42", "45", "48", "50", "55", "60", "65", "70",
            "75", "80", "85", "90", "95", "100", "105", "110", "115", "120", "125",
            "130", "140", "150", "160", "175", "200", "225", "250", "300", "350",
            "400", "450", "500", "555", "600"
    };

    private CustomerGenerator()
    {
        // prevent instantiate
    }

    public static Customer generateNewCustomer()
    {
        String firstName = FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)];
        String streetName = STREET_NAMES[RANDOM.nextInt(STREET_NAMES.length)];
        String streetNumber = STREET_NUMBERS[RANDOM.nextInt(STREET_NUMBERS.length)];

        return new Customer(firstName, lastName, streetName, streetNumber);
    }

    public static List<Customer> generateCustomers(int count)
    {
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            customers.add(CustomerGenerator.generateNewCustomer());
        }
        return customers;
    }
}
