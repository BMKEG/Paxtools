package org.biopax.paxtools.controller;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;

/**
 * Specifically "Clones" the BioPAX elements set
 * (traverses to obtain dependent elements),
 * puts them to the new model using the visitor and traverser framework;
 * ignores elements that are not in the source list (compare to {@link Fetcher})
 *
 * @see org.biopax.paxtools.controller.Visitor
 * @see org.biopax.paxtools.controller.Traverser
 */
public class ShallowCopy implements Visitor
{
	Traverser traverser;

    private BioPAXElement copy;

    public ShallowCopy(EditorMap map)
	{

		traverser = new Traverser(map, this);
	}


    public ShallowCopy(BioPAXLevel l)
    {
        this(new SimpleEditorMap(l));
    }

    public ShallowCopy()
    {
        this(BioPAXLevel.L3);
    }

    /**
	 *
	 * @param model
     * @param source
	 * @param newID
     * @return
	 */
	public <T extends BioPAXElement> T copy(Model model, T source, String newID)
	{
        T copy = model.addNew(((Class<T>) source.getModelInterface()), newID);
        this.copy = copy;
        traverser.traverse(copy,model);
        return copy;

    }


// --------------------- Interface Visitor ---------------------

	public void visit(BioPAXElement domain, Object range, Model model, PropertyEditor editor)
	{
        editor.setValueToBean(range,copy);
	}
}


