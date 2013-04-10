package org.biopax.paxtools.io.sbgn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;

import org.ivis.layout.*;
import org.ivis.layout.util.*;
import org.ivis.layout.cose.CoSELayout;
import org.sbgn.bindings.Arc;
import org.sbgn.bindings.Port;
import org.sbgn.bindings.Glyph;
import org.sbgn.bindings.Sbgn;
import org.sbgn.bindings.Bbox;

/**
 * Class for applying layout by ChiLay component to Sbgn generated by paxtools.
 * @author: istemi Bahceci
 * */

public class SBGNLayoutManager
{
	// Layout and root objects
	private Layout layout;
	private VCompound root;
	
	// mapping between view and layout level 
	private HashMap <VNode, LNode> viewToLayout;
	private HashMap <Glyph,VNode>  glyphToVNode;
	private HashMap <String, Glyph> idToGLyph;
	private HashMap <String, Glyph> idToCompartmentGlyphs;
	private HashMap <String, Glyph> portIDToOwnerGlyph;
	
	/**
	 * Applies CoSE layout to the given SBGN model.
	 * 
	 * @param sbgn Sbgn object to which layout will be applied
	 * @return Laid out sbgn object
	 */
	public Sbgn createLayout(Sbgn sbgn)
	{
		viewToLayout = new HashMap();
		glyphToVNode = new HashMap();
		idToGLyph = new HashMap();
		idToCompartmentGlyphs = new HashMap();
		portIDToOwnerGlyph = new HashMap();
		
		// Using Compound spring  embedder layout
		this.layout = new CoSELayout();
		
		// This list holds the glyphs that will be deleted after corresponding glyph is added to child glyph of another glyph.
		ArrayList <Glyph> deletedList = new ArrayList<Glyph>();
		
		LGraphManager graphMgr = this.layout.getGraphManager(); 
		LGraph lRoot = graphMgr.addRoot();
		this.root = new VCompound(new Glyph());
		
		// Detect compartment glyphs and put them in a hashmap, also set compartment glyphs of members of complexes.
		for (Glyph g: sbgn.getMap().getGlyph()) 
		{
			if(g.getClazz() == "compartment")
			{
				idToCompartmentGlyphs.put(g.getId(), g);
			}
			
			//Add compartment ref to the all children of this node.
			if(g.getCompartmentRef() != null)
			{
				Glyph compartment = (Glyph)g.getCompartmentRef();
				setCompartmentRefForComplexMembers(g, compartment);
			}
		}
		
		
		// Add glyphs to the compartment glyphs according to their "compartmentRef" field.
		for (Glyph g: sbgn.getMap().getGlyph()) 
		{	
			if(g.getCompartmentRef() != null)
			{
				Glyph containerCompartment = (Glyph)g.getCompartmentRef();
				idToCompartmentGlyphs.get(containerCompartment.getId()).getGlyph().add(g);
				deletedList.add(g);	
			}	
		}
		
		// Delete the duplicate glyphs, after they are moved to corresponding compartment glyph.
		for (Glyph g: deletedList) 
		{	
			sbgn.getMap().getGlyph().remove(g);
		}
		
		// initialize the map for keeping ports and their owner glyphs
		// with entries like: <portID, ownerGlyph>
		initPortIdToGlyphMap(sbgn.getMap().getGlyph());
		
		//Remove ports from source and target field of ports
		//replace them with owner glyphs of these ports
		removePortsFromArcs(sbgn.getMap().getArc());
		
		// Assign logical operator and Process nodes to compartment
		assignProcessAndLogicOpNodesToCompartment(sbgn);
					
		// Create Vnodes for ChiLay layout component
		createVNodes(root, sbgn.getMap().getGlyph());
		
		for (VNode vNode: this.root.children) 
		{ 
			this.createLNode(vNode, null, this.layout); 
		}
		
		// Create LEdges for ChiLay layout component
		createLEdges(sbgn.getMap().getArc(), this.layout);
		graphMgr.updateBounds();
		
		// Apply layout
		this.layout.runLayout();
		graphMgr.updateBounds();

		// Update the bounds
		/*for (VNode vNode: this.root.children) 
		{ 
			updateCompoundBounds(vNode.glyph, vNode.glyph.getGlyph()); 
		}*/
		
		// Clear inside of the compartmentGlyphs
		for (Glyph compGlyph: idToCompartmentGlyphs.values()) 
		{
			//Again add the members of compartments
			for(Glyph memberGlyph:compGlyph.getGlyph() )
			{
				sbgn.getMap().getGlyph().add(memberGlyph);
			}
			compGlyph.getGlyph().clear();
		}

		return sbgn;
	}
	
	/**
	 * This method finds process nodes and logical operator nodes in sbgn map and assigns them to a compartment by using majority rule.
	 * @param sbgn Given Sbgn map.
	 * */
	public void assignProcessAndLogicOpNodesToCompartment(Sbgn sbgn)
	{
		// Create a hashmap for keeping a node( generally logical operators and process nodes ) and its neighbours.
		// TreeMap value of the hash map keeps track of compartment nodes that includes neighbours of the node by String id and 
		// Integer value holds the number of occurences of that compartment among the neighbours of the node as parent.
		HashMap <String, TreeMap<String,Integer>>  nodetoNeighbours = new HashMap<String, TreeMap<String,Integer>>();
		List<Glyph> glyphList = sbgn.getMap().getGlyph();
		List<Arc> 	arcList   = sbgn.getMap().getArc();
		
		// Keeps track of process and logical operator nodes that will be assigned to a compartment.
		ArrayList<Glyph> targetNodes = new ArrayList<Glyph>();
		
		//Iterate over glyphs of sbgn map
		for(Glyph glyph: glyphList)
		{
			// Here logical operator nodes and process nodes are interested !
			if(glyph.getClazz() == "process"||glyph.getClazz() == "omitted process"||glyph.getClazz() == "uncertain process" || glyph.getClazz() == "phenotype"||
			   glyph.getClazz() == "association" || glyph.getClazz() == "dissociation"||glyph.getClazz() == "and"||glyph.getClazz() == "or"||glyph.getClazz() == "not") 
			{
				// Add a new value to hash map and also store the node as target node
				String processGlyphID = glyph.getId();
				nodetoNeighbours.put(processGlyphID, new TreeMap(Collections.reverseOrder()));
				targetNodes.add(glyph);
				
				// Iterate over arc list
				for(Arc arc: arcList)
				{
					Glyph target = null;
					Glyph source = null;
					
					// If source and target of node is port find its owner glyph ! else just assign it.
					if(arc.getSource() instanceof Port)
						source = portIDToOwnerGlyph.get(((Port)arc.getSource()).getId());
					else
						source = (Glyph)arc.getSource();
					
					if(arc.getTarget() instanceof Port)
						target = portIDToOwnerGlyph.get(((Port)arc.getTarget()).getId());
					else
						target = (Glyph)arc.getTarget();
					
					// If source of any arc is our node, then target must be neighbour of this node !
					if(source.getId().equals(processGlyphID))
					{
						// if compartment ref of neighbour node is not null, increment its occurence by 1
						if(target.getCompartmentRef() != null)
						{
							Glyph containerCompartment = (Glyph)target.getCompartmentRef();
							
							if(nodetoNeighbours.get(processGlyphID).get(containerCompartment.getId()) != null )
							{
								Integer value = nodetoNeighbours.get(processGlyphID).get(containerCompartment.getId());
								nodetoNeighbours.get(processGlyphID).put(containerCompartment.getId(), value+1 );
							}
							else
								nodetoNeighbours.get(processGlyphID).put(containerCompartment.getId(), 1);
						}
					}	
					
					// same as source part !!
					else if(target.getId().equals(processGlyphID))
					{
						if(target.getCompartmentRef() != null)
						{
							Glyph containerCompartment = (Glyph)source.getCompartmentRef();
							
							if(nodetoNeighbours.get(processGlyphID).get(source.getId()) != null )
							{
								Integer value = nodetoNeighbours.get(processGlyphID).get(containerCompartment.getId());
								nodetoNeighbours.get(processGlyphID).put(containerCompartment.getId(), value+1 );
							}
							else
								nodetoNeighbours.get(processGlyphID).put(containerCompartment.getId(), 1);
						}
					}
				}
			}
		}
		
		//Finally assign nodes to compartments by majority rule
		for(Glyph glyph: targetNodes)
		{
			String id = glyph.getId();
			TreeMap <String, Integer> tMap = nodetoNeighbours.get(id);
			if(tMap.size() > 0)
			{
				Glyph compartment = idToCompartmentGlyphs.get(tMap.firstKey());
				compartment.getGlyph().add(glyph);
				
				//Remove it from sbgn also
				sbgn.getMap().getGlyph().remove(glyph);
			}
		}
	}
	
	/**
	 * Updates bounds of a compound node ( i.e. complex glyph ) from its children .
	 * @param parent compound glyph.
	 * @param childGlyphs related children of parent .
	 * */
	public void updateCompoundBounds(Glyph parent,List<Glyph> childGlyphs)
	{		
		float PAD = (float) 2.0;
		float minX = Float.MAX_VALUE; float minY = Float.MAX_VALUE;
		float maxX = Float.MIN_VALUE; float maxY = Float.MIN_VALUE;
		
		for (Glyph tmpGlyph:childGlyphs) 
		{
			if(tmpGlyph.getClazz() != "unit of information" && tmpGlyph.getClazz() != "state variable" )
			{
				if(tmpGlyph.getGlyph().size() > 0)
					updateCompoundBounds(tmpGlyph, tmpGlyph.getGlyph());
				
	            float w = tmpGlyph.getBbox().getW();
				float h = tmpGlyph.getBbox().getH();
				
	            // Verify MIN and MAX x/y again:
	            minX = Math.min(minX, (tmpGlyph.getBbox().getX()));
	            minY = Math.min(minY, (tmpGlyph.getBbox().getY()));
	            maxX = Math.max(maxX, (tmpGlyph.getBbox().getX())+w);
	            maxY = Math.max(maxY, (tmpGlyph.getBbox().getY())+h);
	            
	            if (minX == Float.MAX_VALUE) minX = 0;
	            if (minY == Float.MAX_VALUE) minY = 0;
	            if (maxX == Float.MIN_VALUE) maxX = 0;
	            if (maxY == Float.MIN_VALUE) maxY = 0;
	            
	            parent.getBbox().setX(minX - PAD);
	            parent.getBbox().setY(minY - PAD);
	            parent.getBbox().setW(maxX -  parent.getBbox().getX() + PAD);
	            parent.getBbox().setH(maxY -  parent.getBbox().getY() + PAD);
			}
		}
	}

	/**
	 * Recursively creates VNodes from Glyphs of Sbgn. 
	 * 
	 * @param parent Parent of the glyphs that are passed as second arguement.
	 * @param glyphs Glyphs that are child of parent which is passed as first arguement.
	 * 
	 * */
	public void createVNodes(VCompound parent,List<Glyph> glyphs)
	{
		for(Glyph glyph: glyphs )
		{	
			if (glyph.getClazz() !=  "state variable" && glyph.getClazz() !=  "unit of information"  ) 
			{	
				if(glyph.getClazz() ==  "process")
				{
					VCompound v = new VCompound(glyph);
				}
				
				if(!this.isChildless(glyph))
				{
					VCompound v = new VCompound(glyph);

					idToGLyph.put(glyph.getId(), glyph);
					glyphToVNode.put(glyph, v);
					parent.children.add(v);
					createVNodes(v, glyph.getGlyph());
				}
				
				else
				{
					VNode v = new VNode(glyph);
					idToGLyph.put(glyph.getId(), glyph);
					glyphToVNode.put(glyph, v);		
					parent.children.add(v);
				}
			}
		}
	}

	/**
	 * Creates LNodes from Arcs of Sbgn and adds it to the passed layout object. 
	 * 
	 * @param arcs List of arc objects from which the LEdges will be constructed for ChiLay Layout component.
	 * @param layout layout object to which the created LEdges added.
	 * 
	 * */
	public void createLEdges(List<Arc> arcs, Layout layout)
	{
		for(Arc arc: arcs )
		{
			LEdge lEdge = layout.newEdge(null);	
			LNode sourceLNode = this.viewToLayout.get(glyphToVNode.get(arc.getSource()));
			LNode targetLNode = this.viewToLayout.get(glyphToVNode.get(arc.getTarget()));
			// Add edge to the layout
			this.layout.getGraphManager().add(lEdge, sourceLNode, targetLNode);
		}
	}
	
	/**
	 * Helper function for creating LNode objects from VNode objects and adds them to the given layout.
	 * 
	 * @param vNode  VNode object from which a corresponding LNode object will be created.
	 * @param parent parent of vNode, if not null vNode will be added to layout as child node.
	 * @param layout layout object to which the created LNodes added.
	 * */
	
	public void createLNode(VNode vNode,VNode parent,Layout layout)
	{
		LNode lNode = layout.newNode(vNode);
		LGraph rootLGraph = layout.getGraphManager().getRoot();
		this.viewToLayout.put(vNode, lNode); 
		
		// if the vNode has a parent, add the lNode as a child of the parent l-node. 
		// otherwise, add the node to the root graph. 
		if (parent != null) 
		{ 
			LNode parentLNode = this.viewToLayout.get(parent); 
			parentLNode.getChild().add(lNode); 
		} 
		
		else 
		{ 
			rootLGraph.add(lNode); 
		}		
		
		lNode.setLocation(vNode.glyph.getBbox().getX(),vNode.glyph.getBbox().getY());
			
		if (vNode instanceof VCompound) 
		{ 
			VCompound vCompound = (VCompound) vNode; 
			// add new LGraph to the graph manager for the compound node 
			layout.getGraphManager().add(layout.newGraph(null), lNode); 
			// for each VNode in the node set create an LNode 
			for (VNode vChildNode: vCompound.getChildren()) 
			{ 
				this.createLNode(vChildNode, vCompound, layout); 
			} 		
		}
		
		else
		{
			lNode.setWidth(vNode.glyph.getBbox().getW());
			lNode.setHeight(vNode.glyph.getBbox().getH());
		}
	}
	
	/**
	 * This method recursively set compartmentRef fields of members of any complex glyphs 
	 * as same as complex's compartmentRef
	 * 
	 * @params glyph target glyph whose compartmentRef(compartment parameter) will be set.
	 * @params compartment compartmentRef value that will be set.
	 * */
	public void setCompartmentRefForComplexMembers(Glyph glyph, Glyph compartment)
	{
		glyph.setCompartmentRef(compartment);
		if(glyph.getCompartmentRef() != null && glyph.getGlyph().size() > 0)
		{
			for(Glyph g: glyph.getGlyph() )
			{
				setCompartmentRefForComplexMembers(g, compartment);
			}
		}	
	}
	
	/**
	 * This method replaces ports of arc objects with their owners.
	 * @params arcs Arc list of sbgn model 
	 * */
	public void removePortsFromArcs(List<Arc> arcs)
	{
		for(Arc arc: arcs )
		{			
			// If source is port, first clear port indicators else retrieve it from hashmaps
			if (arc.getSource() instanceof Port ) 
			{
				Glyph source = portIDToOwnerGlyph.get(((Port)arc.getSource()).getId());
				arc.setSource(source);
			}
			
			// If target is port, first clear port indicators else retrieve it from hashmaps
			if (arc.getTarget() instanceof Port) 
			{
				Glyph target = portIDToOwnerGlyph.get(((Port)arc.getTarget()).getId());
				arc.setTarget(target);
			}
		}
	}
	
	/**
	 * This method initializes map for glyphs and their respective ports.
	 * @params glyphs Glyph list of sbgn model 
	 * */
	public void initPortIdToGlyphMap(List<Glyph> glyphs)
	{
		for(Glyph glyph: glyphs)
		{		
				for(Port p: glyph.getPort())
				{
					portIDToOwnerGlyph.put(p.getId(), glyph );
				}
				if(glyph.getGlyph().size() > 0)
					initPortIdToGlyphMap(glyph.getGlyph());
		}
	}
	
	/**
	 * Returns true if a glyph includes child glyphs(state and info glyphs are out of count !)
	 * @params targetGlyph target glyph that will be queried. 
	 * */
	public boolean isChildless(Glyph targetGlyph)
	{
		boolean checker = true;
		for(Glyph glyph: targetGlyph.getGlyph() )
		{
			if (glyph.getClazz() !=  "state variable" && glyph.getClazz() !=  "unit of information"  ) 
			{
				checker = false;
				break;
			}
		}
		return checker;
	}
}