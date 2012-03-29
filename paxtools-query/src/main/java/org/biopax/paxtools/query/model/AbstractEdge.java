package org.biopax.paxtools.query.model;

/**
 * @author Ozgun Babur
 */
public abstract class AbstractEdge implements Edge
{
	private Node source;
	private Node target;
	private Graph graph;

	public AbstractEdge(Node source, Node target, Graph graph)
	{
		this.source = source;
		this.target = target;
		this.graph = graph;
	}

	public Node getTargetNode()
	{
		return target;
	}

	public Node getSourceNode()
	{
		return source;
	}

	public Graph getGraph()
	{
		return graph;
	}

	public String getKey()
	{
		return source.getKey() + "|" + target.getKey();
	}

	@Override
	public int hashCode()
	{
		return source.hashCode() + target.hashCode() + graph.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof AbstractEdge)
		{
			AbstractEdge e = (AbstractEdge) obj;
			return source == e.getSourceNode() &&
				target == e.getTargetNode() &&
				graph == e.getGraph();
		}
		return false;
	}

	@Override
	public int getSign()
	{
		return 1;
	}

	public void clear()
	{
	}

}
