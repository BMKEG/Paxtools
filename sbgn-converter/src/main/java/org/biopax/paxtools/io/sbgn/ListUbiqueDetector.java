package org.biopax.paxtools.io.sbgn;

import org.biopax.paxtools.model.level3.PhysicalEntity;

import java.util.Set;

/**
 * Detects ubiquitous molecules using a given ID set.
 *
 * @author Ozgun Babur
 */
public class ListUbiqueDetector implements UbiqueDetector
{
	/**
	 * IDs of ubiques.
	 */
	Set<String> ubiqueIDs;

	/**
	 * Contructor with the Ubique IDs.
	 * @param ubiqueIDs IDs of ubiques
	 */
	public ListUbiqueDetector(Set<String> ubiqueIDs)
	{
		this.ubiqueIDs = ubiqueIDs;
	}

	/**
	 * Checks if the ID of the PhysicalEntity is in the set.
	 * @param pe PhysicalEntity to check
	 * @return true if ubique
	 */
	@Override
	public boolean isUbique(PhysicalEntity pe)
	{
		return ubiqueIDs.contains(pe.getUri());
	}
}
