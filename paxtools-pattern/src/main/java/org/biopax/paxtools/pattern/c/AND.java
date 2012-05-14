package org.biopax.paxtools.pattern.c;

import org.biopax.paxtools.pattern.Constraint;
import org.biopax.paxtools.pattern.Match;
import org.biopax.paxtools.model.BioPAXElement;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Ozgun Babur
 */
public class AND extends OR
{
	public AND(Constraint... con)
	{
		super(con);
	}

	@Override
	public boolean satisfies(Match match, int... ind)
	{
		for (Constraint constr : con)
		{
			if (!constr.satisfies(match, ind)) return false;
		}
		return true;
	}

	@Override
	public Collection<BioPAXElement> generate(Match match, int... ind)
	{
		Collection<BioPAXElement> gen = new HashSet<BioPAXElement> (con[0].generate(match, ind));

		for (int i = 1; i < con.length; i++)
		{
			if (gen.isEmpty()) break;

			gen.retainAll(con[i].generate(match, ind));
		}
		return gen;
	}
}