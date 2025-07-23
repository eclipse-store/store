package org.eclipse.store.examples.gigamap;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javafaker.service.RandomService;

public enum Interest
{
	SPORTS,
	LITERATURE,
	PARTY,
	CHARITY;
	
	
	static List<Interest> random(final RandomService random)
	{
		final Interest[] values = Interest.values();
		final int count = random.nextInt(1, values.length);
		return IntStream.rangeClosed(0, count)
			.map(i -> random.nextInt(count))
			.mapToObj(i -> values[i])
			.distinct()
			.collect(Collectors.toList())
		;
	}
}
