/**
 * Copyright (c) 2011-2012 Eclipse contributors and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.emf.ecore.xcore.scoping;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.codegen.ecore.genmodel.GenBase;
import org.eclipse.emf.codegen.ecore.genmodel.GenClass;
import org.eclipse.emf.codegen.ecore.genmodel.GenClassifier;
import org.eclipse.emf.codegen.ecore.genmodel.GenFeature;
import org.eclipse.emf.codegen.ecore.genmodel.GenOperation;
import org.eclipse.emf.codegen.ecore.genmodel.GenTypeParameter;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EGenericType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ETypeParameter;
import org.eclipse.emf.ecore.xcore.XClass;
import org.eclipse.emf.ecore.xcore.XGenericType;
import org.eclipse.emf.ecore.xcore.XOperation;
import org.eclipse.emf.ecore.xcore.XReference;
import org.eclipse.emf.ecore.xcore.XcorePackage;
import org.eclipse.emf.ecore.xcore.mappings.XcoreMapper;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.AbstractScope;
import org.eclipse.xtext.xbase.scoping.XbaseScopeProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.JvmFeatureScope;
import org.eclipse.xtext.xbase.scoping.featurecalls.LocalVarDescription;

import com.google.common.collect.Lists;
import com.google.inject.Inject;


/**
 * This class contains custom scoping description.
 *
 * see : http://www.eclipse.org/Xtext/documentation/latest/xtext.html#scoping on
 * how and when to use it
 *
 */
public class XcoreScopeProvider extends XbaseScopeProvider
{
  @Inject
  private XcoreMapper mapper;

  @Inject
  private IQualifiedNameConverter qualifiedNameConverter;

  @Override
  protected IScope createLocalVarScopeForJvmDeclaredType(JvmDeclaredType type, IScope parentScope)
  {
    EList<JvmTypeReference> superTypes = type.getSuperTypes();
    if (superTypes.isEmpty())
    {
      return new JvmFeatureScope(parentScope, "this", new LocalVarDescription(THIS, type));
    }
    else
    {
      return
        new JvmFeatureScope
          (parentScope,
           "this & super",
           Lists.newArrayList( new LocalVarDescription(THIS, type),  new LocalVarDescription(SUPER, superTypes.get(0).getType())));
    }
  }

  @Override
  protected JvmDeclaredType getContextType(EObject call)
  {
    if (call == null)
    {
      return null;
    }
    else
    {
      XClass containerClass = EcoreUtil2.getContainerOfType(call, XClass.class);
      return
        containerClass != null ?
          mapper.getMapping(containerClass).getInterfaceType() : // TODO use implementation class
          super.getContextType(call);
    }
  }

  @Override
  public IScope getScope(final EObject context, EReference reference)
  {
    if (reference == XcorePackage.Literals.XREFERENCE__OPPOSITE)
    {
      return
        new AbstractScope(IScope.NULLSCOPE, false)
        {
          @Override
          protected Iterable<IEObjectDescription> getAllLocalElements()
          {
            ArrayList<IEObjectDescription> result = new ArrayList<IEObjectDescription>();
            if (context instanceof XReference)
            {
              XReference xReference = (XReference)context;
              XGenericType type = xReference.getType();
              if (type != null)
              {
                GenBase genType = type.getType();
                if (genType instanceof GenTypeParameter)
                {
                  GenTypeParameter genTypeParameter = (GenTypeParameter)genType;
                  ETypeParameter eTypeParameter = genTypeParameter.getEcoreTypeParameter();
                  for (EGenericType eGenericType : eTypeParameter.getEBounds())
                  {
                    EClassifier eRawType = eGenericType.getERawType();
                    if (eRawType instanceof EClass)
                    {
                      genType = genType.getGenModel().findGenClassifier(eRawType);
                      break;
                    }
                  }
                }
                if (genType instanceof GenClass)
                {
                  GenClass genClass = (GenClass)genType;
                  for (GenFeature opposite : genClass.getGenFeatures())
                  {
                    if (opposite.isReferenceType())
                    {
                      String name = opposite.getName();
                      if (name != null)
                      {
                        result.add(new EObjectDescription(qualifiedNameConverter.toQualifiedName(name), opposite, null));
                      }
                    }
                  }
                }
              }
            }
            return result;
          }
        };
    }
    else if (reference == XcorePackage.Literals.XREFERENCE__KEYS)
    {
      return
        new AbstractScope(IScope.NULLSCOPE, false)
        {
          @Override
          protected Iterable<IEObjectDescription> getAllLocalElements()
          {
            ArrayList<IEObjectDescription> result = new ArrayList<IEObjectDescription>();
            if (context instanceof XReference)
            {
              XReference ref = (XReference)context;
              GenFeature genFeature = mapper.getMapping(ref).getGenFeature();
              if (genFeature != null)
              {
                GenClass genClass = genFeature.getTypeGenClass();
                if (genClass != null)
                {
                  for (GenFeature key : genClass.getAllGenFeatures())
                  {
                    if (!key.isReferenceType())
                    {
                      String name = key.getName();
                      if (name != null)
                      {
                        result.add(new EObjectDescription(qualifiedNameConverter.toQualifiedName(name), key, null));
                      }
                    }
                  }
                }
              }
            }
            return result;
          }
        };

    }
    else
    {
      IScope scope = super.getScope(context, reference);
      return
        reference == XcorePackage.Literals.XGENERIC_TYPE__TYPE ?
          new TypeParameterScope(scope, false, context) :
          scope;
    }
  }

  protected class TypeParameterScope extends AbstractScope
  {
    private final EObject context;

    public TypeParameterScope(IScope parent, boolean ignoreCase, EObject context)
    {
      super(parent, ignoreCase);
      this.context = context;
    }

    void handleGenTypeParameters(List<IEObjectDescription> result, EList<GenTypeParameter> genTypeParameters)
    {
      for (final GenTypeParameter genTypeParameter : genTypeParameters)
      {
        result.add
          (new EObjectDescription
             (qualifiedNameConverter.toQualifiedName(genTypeParameter.getName()),
              genTypeParameter,
              null));
      }
    }

    @Override
    protected Iterable<IEObjectDescription> getAllLocalElements()
    {
      ArrayList<IEObjectDescription> result = new ArrayList<IEObjectDescription>();
      for (EObject eObject = context; eObject != null; eObject = eObject.eContainer())
      {
        if (eObject instanceof XOperation)
        {
          GenOperation genOperation = mapper.getMapping((XOperation)eObject).getGenOperation();
          if (genOperation != null)
          {
            handleGenTypeParameters(result, genOperation.getGenTypeParameters());
          }
        }
        else if (eObject instanceof XClass)
        {
          GenClassifier genClassifier = mapper.getMapping((XClass)eObject).getGenClass();
          if (genClassifier != null)
          {
            handleGenTypeParameters(result, genClassifier.getGenTypeParameters());
          }
          break;
        }
      }
      return result;
    }
  }
}
