package org.eclipse.store.examples.gigamap;

import com.github.javafaker.Faker;

public class Address
{
	private final String street;
	private final String city;
	private final String country;
	
	public Address(final Faker faker)
	{
		this(
			faker.address().streetAddress(),
			faker.address().city(),
			faker.address().country()
		);
	}
	
	public Address(final String street, final String city, final String country)
	{
		super();
		this.street  = street;
		this.city    = city;
		this.country = country;
	}

	public String getStreet()
	{
		return this.street;
	}

	public String getCity()
	{
		return this.city;
	}

	public String getCountry()
	{
		return this.country;
	}

	@Override
	public String toString()
	{
		return "Address [street=" + this.street + ", city=" + this.city + ", country=" + this.country + "]";
	}
		
}
