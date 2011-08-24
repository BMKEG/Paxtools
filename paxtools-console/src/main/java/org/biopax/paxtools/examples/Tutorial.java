package org.biopax.paxtools.examples;

import org.biopax.paxtools.controller.*;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.query.QueryExecuter;
import org.biopax.paxtools.query.algorithm.Direction;
import org.biopax.paxtools.util.Filter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Tutorial
{


 public static void myFirstModel()
 {
  BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
  Model model = factory.createModel();
  Protein protein1 = model.addNew(Protein.class,
                                  "http://biopax.org/tutorial/test1");
  protein1.addName("Tutorial Example Small molecule Transporter 1");
  protein1.setDisplayName("TEST1");

  BiochemicalReaction rxn1 = model.addNew(BiochemicalReaction.class,
                                          "http://biopax.org/tutorial/rxn1");
  rxn1.addLeft(protein1);

 }


 public static void IO(InputStream inputStreamFromFile,
                       OutputStream outputStream)
 {
  BioPAXIOHandler handler = new SimpleIOHandler(); // auto-detects Level
  Model model = handler.convertFromOWL(inputStreamFromFile);
  handler.convertToOWL(model, outputStream);
  String id1 = null, id2 = null, id3 = null;
  handler.convertToOWL(model, outputStream, id1, id2, id3);

 }

 public static void tempted(BioPAXIOHandler handler, InputStream inputStream,
                            OutputStream outputStream)
 {
  //read initial model
  Model model1 = handler.convertFromOWL(inputStream);
  //create an empty model
  Model model2 = model1.getLevel().getDefaultFactory().createModel();
  //extract reaction
  model2.add(model1.getByID("The_reaction_id"));
  //write it out
  handler.convertToOWL(model2, outputStream);
 }


 /**
  * A controller that excises/extracts an element and all the elements it is
  * dependent on from a model and adds them into a new model.
  */
 class Excisor implements Visitor
 {
  private Traverser traverser;

  private EditorMap editorMap;

  private Model targetModel;

  public Excisor(EditorMap editorMap)
  {
   this.editorMap = editorMap;
   this.traverser = new Traverser(editorMap, this);
  }

  public Excisor(EditorMap editorMap, boolean filtering)
  {
   this.editorMap = editorMap;
   if (filtering)
   //We will filter nextStep property, as Reactome pathways leads
   //outside the current pathway. Step processes are listed in the
   //pathwayComponent property as well so this does not affect the fetcher.
   {
    final Filter<PropertyEditor> nextStepFilter = new Filter<PropertyEditor>()
    {
     public boolean filter(PropertyEditor editor)
     {
      return !editor.getProperty().equals("nextStep");
     }
    };
    this.traverser = new Traverser(editorMap, this, nextStepFilter);
   }
   else this.traverser =  new Traverser(editorMap, this);
  }

  //The visitor will add all elements that are reached into the new model,
  // and recursively traverse it
  public void visit(BioPAXElement domain, Object range, Model model,
                    PropertyEditor editor)
  {
   // We are only interested in the BioPAXElements since
   // primitive fields are always copied by value
   if (range != null && range instanceof BioPAXElement)
   {
    BioPAXElement bpe = (BioPAXElement) range;

    if (!targetModel.contains(bpe))
    {
     targetModel.add(bpe);
     traverser.traverse(bpe, model);
    }
   }
  }


  public Model excise(Model sourceModel, String... ids)
  {
   // Create a new model that will contain the element(s) of interest
   this.targetModel = editorMap.getLevel().getDefaultFactory().createModel();

   for (String id : ids)
   {
    // Get the BioPAX element
    BioPAXElement bpe = sourceModel.getByID(id);
    // Add it to the model
    targetModel.add(bpe);
    // Add the elements that bpe is dependent on
    traverser.traverse(bpe, sourceModel);
   }

   return targetModel;
  }
 }

 public static void merge(EditorMap editorMap, Model srcModel2,
                          Model srcModel1)
 {
  Model targetModel = editorMap.getLevel().getDefaultFactory().createModel();
  Merger merger = new Merger(editorMap);
  merger.merge(targetModel, srcModel1, srcModel2);

 }

 public Set access1(Complex complex)
 {
  Set<UnificationXref> xrefs = new HashSet<UnificationXref>();
  recursivelyObtainMembers(complex, xrefs);
  return xrefs;
 }

 private void recursivelyObtainMembers(Complex complex,
                                       Set<UnificationXref> xrefs)
 {
  for (PhysicalEntity pe : complex.getComponent())
  {
   if (pe instanceof Complex)
   {
    recursivelyObtainMembers((Complex) pe, xrefs);
   } else
   {
    Set<Xref> memberxrefs =
      ((SimplePhysicalEntity) pe).getEntityReference().getXref();
    for (Xref xref : memberxrefs)
    {
     if (xref instanceof UnificationXref)
     {
      xrefs.add((UnificationXref) xref);
     }
    }
   }

  }
 }

 public Set access2(Complex complex)
 {
  return new PathAccessor(
    "Complex/component*/EntityReference/xref:UnificationXref",
    BioPAXLevel.L3).getValueFromBean(complex);
 }

 public void graphQuery(Model model, PhysicalEntity entity3,
                        PhysicalEntity entity2, PhysicalEntity entity1)
 {
  Set<BioPAXElement> sourceSet = new HashSet<BioPAXElement>();

  // Add the related source PhysicalEntity (or children) objects to the
  // source set
  Collections.addAll(sourceSet, entity1, entity2, entity3);

  int limit = 2;

  // Direction can be upstream, downstream, or bothstream.
  Direction direction = Direction.BOTHSTREAM;


  Set<BioPAXElement> result = QueryExecuter.runNeighborhood(sourceSet, model,
                                                            limit, direction);

  Completer c = new Completer(SimpleEditorMap.get(BioPAXLevel.L3));
  result = c.complete(result, model);
 }


 public void highlightWorkaround()
 {
  myFirstModel();
  IO(null, null);
  Excisor ex = new Excisor(null);
  access1(null);
  graphQuery(null, null, null, null);
 }


}
