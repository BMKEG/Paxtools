package org.biopax.paxtools.impl.level3;

import org.biopax.paxtools.model.level3.NucleicAcid;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.TemplateDirectionType;
import org.biopax.paxtools.model.level3.TemplateReaction;
import org.biopax.paxtools.util.BPCollections;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.*;
import org.hibernate.search.annotations.Indexed;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Proxy(proxyClass= TemplateReaction.class)
@Indexed
@DynamicUpdate @DynamicInsert
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class TemplateReactionImpl extends InteractionImpl implements TemplateReaction {
    private Set<PhysicalEntity> product;
    private NucleicAcid template;
	private TemplateDirectionType templateDirection;

	public TemplateReactionImpl()
	{
        this.product = BPCollections.createSafeSet();
    }
	
	@Transient
    public Class<? extends TemplateReaction> getModelInterface()
	{
		return TemplateReaction.class;
	}

    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @ManyToMany(targetEntity = PhysicalEntityImpl.class) //TODO: make sequence entity?
    @JoinTable(name="product") 	
    public Set<PhysicalEntity> getProduct()
    {
        return product;
    }

    protected void setProduct(Set<PhysicalEntity> product)
    {
        this.product = product;
    }

    public void addProduct(PhysicalEntity product)
    {
    	if(product != null) {
    		this.product.add(product);
    		super.addParticipant(product);
    	}
    }

    public void removeProduct(PhysicalEntity product)
    {
    	if(product != null) {
    		super.removeParticipant(product);
        	this.product.remove(product);
    	}
    }

	@ManyToOne(targetEntity = NucleicAcidImpl.class)//, cascade = { CascadeType.ALL })
	protected NucleicAcid getTemplateX() {
		return this.template;
	}
	protected void setTemplateX(NucleicAcid template) {
		this.template = template;
	}
    
    @Transient
	public NucleicAcid getTemplate()
     {
         return this.template;
     }

    public void setTemplate(NucleicAcid template)
    {
         if(this.template!= null)
         {
            super.removeParticipant(this.template);
         }
         if(template != null) {
        	 this.template=template;
        	 super.addParticipant(template);
         }
    }

    @Enumerated
	public TemplateDirectionType getTemplateDirection()
	{
		return templateDirection;
	}

	public void setTemplateDirection(TemplateDirectionType templateDirection)
	{
		this.templateDirection = templateDirection;
	}

}
