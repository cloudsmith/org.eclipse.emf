package org.eclipse.emf.ecore.resource.impl;

import java.util.Date;
import java.util.Map;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.FeatureMap;

public class BinaryIO
{
  protected enum FeatureKind
  {
    EOBJECT_CONTAINER,
    EOBJECT_CONTAINER_PROXY_RESOLVING,

    EOBJECT,
    EOBJECT_PROXY_RESOLVING,

    EOBJECT_LIST,
    EOBJECT_LIST_PROXY_RESOLVING,

    EOBJECT_CONTAINMENT,
    EOBJECT_CONTAINMENT_PROXY_RESOLVING,

    EOBJECT_CONTAINMENT_LIST,
    EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING,

    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT,
    LONG,
    SHORT,
    STRING,

    /**
     * @since 2.7
     */
    DATE,

    /**
     * @since 2.8
     */
    ENUMERATOR,

    DATA,
    DATA_LIST,

    FEATURE_MAP;

    public static BinaryIO.FeatureKind get(EStructuralFeature eStructuralFeature)
    {
      if (eStructuralFeature instanceof EReference)
      {
        EReference eReference = (EReference)eStructuralFeature;
        if (eReference.isContainment())
        {
          if (eReference.isResolveProxies())
          {
            if (eReference.isMany())
            {
              return EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING;
            }
            else
            {
              return EOBJECT_CONTAINMENT_PROXY_RESOLVING;
            }
          }
          else
          {
            if (eReference.isMany())
            {
              return EOBJECT_CONTAINMENT_LIST;
            }
            else
            {
              return EOBJECT_CONTAINMENT;
            }
          }
        }
        else if (eReference.isContainer())
        {
          if (eReference.isResolveProxies())
          {
            return EOBJECT_CONTAINER_PROXY_RESOLVING;
          }
          else
          {
            return EOBJECT_CONTAINER;
          }
        }
        else if (eReference.isResolveProxies())
        {
          if (eReference.isMany())
          {
            return EOBJECT_LIST_PROXY_RESOLVING;
          }
          else
          {
            return EOBJECT_PROXY_RESOLVING;
          }
        }
        else
        {
          if (eReference.isMany())
          {
            return EOBJECT_LIST;
          }
          else
          {
            return EOBJECT;
          }
        }
      }
      else
      {
        EAttribute eAttribute = (EAttribute)eStructuralFeature;
        EDataType eDataType = eAttribute.getEAttributeType();
        String instanceClassName = eDataType.getInstanceClassName();
        if (instanceClassName == "org.eclipse.emf.ecore.util.FeatureMap$Entry")
        {
          return FEATURE_MAP;
        }
        else if (eAttribute.isMany())
        {
          return DATA_LIST;
        }
        else if (instanceClassName == "java.lang.String")
        {
          return STRING;
        }
        else if (instanceClassName == "boolean")
        {
          return BOOLEAN;
        }
        else if (instanceClassName == "byte")
        {
          return BYTE;
        }
        else if (instanceClassName == "char")
        {
          return CHAR;
        }
        else if (instanceClassName == "double")
        {
          return DOUBLE;
        }
        else if (instanceClassName == "float")
        {
          return FLOAT;
        }
        else if (instanceClassName == "int")
        {
          return INT;
        }
        else if (instanceClassName == "long")
        {
          return LONG;
        }
        else if (instanceClassName == "short")
        {
          return SHORT;
        }
        else if (instanceClassName == "java.util.Date")
        {
          return DATE;
        }
        else if (eDataType instanceof EEnum)
        {
          return ENUMERATOR;
        }
        else
        {
          return DATA;
        }
      }
    }
  }

  public enum Version
  {
    VERSION_1_0,

    /**
     * This version supports styles.
     * An extra integer value encoding the style is written after the version number so that deserialization will respect the styles used during serialization.
     * @since 2.7
     */
    VERSION_1_1
  }

  /**
   * @see BinaryIO#OPTION_STYLE_BINARY_FLOATING_POINT
   * @since 2.7
   */
  public static final int STYLE_BINARY_FLOATING_POINT = 0x1;

  /**
   * @see BinaryIO#OPTION_STYLE_BINARY_DATE
   * @since 2.7
   */
  public static final int STYLE_BINARY_DATE = 0x2;

  /**
   * @see BinaryIO#OPTION_STYLE_PROXY_ATTRIBUTES
   * @since 2.7
   */
  public static final int STYLE_PROXY_ATTRIBUTES = 0x4;

  /**
   * @see BinaryIO#OPTION_STYLE_BINARY_ENUMERATOR
   * @since 2.8
   */
  public static final int STYLE_BINARY_ENUMERATOR = 0x8;

  protected static int getStyle(Map<?, ?> options)
  {
    int result = 0;
    if (options != null)
    {
      if (Boolean.TRUE.equals(options.get(BinaryIO.OPTION_STYLE_BINARY_FLOATING_POINT)))
      {
        result |= STYLE_BINARY_FLOATING_POINT;
      }
      if (Boolean.TRUE.equals(options.get(BinaryIO.OPTION_STYLE_BINARY_DATE)))
      {
        result |= STYLE_BINARY_DATE;
      }
      if (Boolean.TRUE.equals(options.get(BinaryIO.OPTION_STYLE_PROXY_ATTRIBUTES)))
      {
        result |= STYLE_PROXY_ATTRIBUTES;
      }
      if (Boolean.TRUE.equals(options.get(BinaryIO.OPTION_STYLE_BINARY_ENUMERATOR)))
      {
        result |= STYLE_BINARY_ENUMERATOR;
      }
    }
    return result;
  }
  protected BinaryIO.Version version;
  /**
   * @since 2.7
   */
  protected int style;
  protected Resource resource;
  protected URI baseURI;
  protected Map<?, ?> options;
  protected char[] characters;

  protected InternalEObject[][] internalEObjectDataArrayBuffer = new InternalEObject[50][];

  protected int internalEObjectDataArrayBufferCount = -1;

  protected FeatureMap.Entry.Internal[][] featureMapEntryDataArrayBuffer = new FeatureMap.Entry.Internal[50][];

  protected int featureMapEntryDataArrayBufferCount = -1;

  /**
	 * A Boolean save option to specify whether {@link Enumerator enumerator} values will be serialized using {@link Enumerator#getValue()} rather than a string representation.
	 * This style option is only supported for serializations with {@link Version#VERSION_1_1 version 1.1} or higher.
	 * The default is false.
	 * @see STYLE_BINARY_ENUMERATOR
	 * @since 2.8
	 */
	public static final String OPTION_STYLE_BINARY_ENUMERATOR = "BINARY_ENUMERATOR";

  /**
	 * A Boolean save option to specify whether serialized proxies will include the serialization of their attribute values.
	 * This style option is only supported for serializations with {@link Version#VERSION_1_1 version 1.1} or higher. 
	 * The default is false.
	 * @see STYLE_PROXY_ATTRIBUTES
	 * @since 2.7
	 */
	public static final String OPTION_STYLE_PROXY_ATTRIBUTES = "PROXY_ATTRIBUTES";
  /**
	 * A Boolean save option to specify whether {@link Date date} values will be serialized using {@link Date#getTime()} rather than a string representation.
	 * This style option is only supported for serializations with {@link Version#VERSION_1_1 version 1.1} or higher.
	 * The default is false.
	 * @see STYLE_BINARY_DATE
	 * @since 2.7
	 */
	public static final String OPTION_STYLE_BINARY_DATE = "BINARY_DATE";

	/**
	 * A save option to specify the {@link Version} to be used for the serialization.
	 * @see Version
	 * @since 2.7
	 */
	public static final String OPTION_VERSION = "VERSION";

	/**
	 * A Boolean save option to specify whether float and double values
	 * are encoded using {@link Float#floatToIntBits(float)} and {@link Double#doubleToLongBits(double)} respectively,
	 * rather than a string representation.
	 * The default is false because GWT's client doesn't support this method.
	 * This style option is only supported for serializations with {@link Version#VERSION_1_1 version 1.1} or higher.
	 * @see STYLE_BINARY_FLOATING_POINT
	 * @since 2.7
	 */
	public static final String OPTION_STYLE_BINARY_FLOATING_POINT = "BINARY_FLOATING_POINT ";

	protected FeatureMap.Entry.Internal [] allocateFeatureMapEntryArray(int length)
  {
    if (featureMapEntryDataArrayBufferCount == -1)
    {
      return new FeatureMap.Entry.Internal[length];
    }
    else
    {
      FeatureMap.Entry.Internal [] buffer = featureMapEntryDataArrayBuffer[featureMapEntryDataArrayBufferCount];
      featureMapEntryDataArrayBuffer[featureMapEntryDataArrayBufferCount--] = null;
      return buffer.length >= length ? buffer :  new FeatureMap.Entry.Internal[length];
    }
  }

	protected InternalEObject [] allocateInternalEObjectArray(int length)
  {
    if (internalEObjectDataArrayBufferCount == -1)
    {
      return new InternalEObject[length];
    }
    else
    {
      InternalEObject [] buffer = internalEObjectDataArrayBuffer[internalEObjectDataArrayBufferCount];
      internalEObjectDataArrayBuffer[internalEObjectDataArrayBufferCount--] = null;
      return buffer.length >= length ? buffer :  new InternalEObject[length];
    }
  }

	protected URI deresolve(URI uri)
  {
    if (baseURI != null && !uri.isRelative())
    {
      URI deresolvedURI = uri.deresolve(baseURI, true, true, false);
      if (deresolvedURI.hasRelativePath() && (!uri.isPlatform() || uri.segment(0).equals(baseURI.segment(0))))
      {
        uri = deresolvedURI;
      }
    }
    return uri;
  }

  protected void recycle(FeatureMap.Entry.Internal[] values)
  {
    if (++featureMapEntryDataArrayBufferCount >= featureMapEntryDataArrayBuffer.length)
    {
      FeatureMap.Entry.Internal [][] newFeatureMapEntryDataArrayBuffer = new FeatureMap.Entry.Internal[featureMapEntryDataArrayBufferCount * 2][];
      System.arraycopy(featureMapEntryDataArrayBuffer, 0, newFeatureMapEntryDataArrayBuffer, 0, featureMapEntryDataArrayBufferCount);
      featureMapEntryDataArrayBuffer = newFeatureMapEntryDataArrayBuffer;
    }
    featureMapEntryDataArrayBuffer[featureMapEntryDataArrayBufferCount] = values;
  }

  protected void recycle(InternalEObject[] values)
  {
    if (++internalEObjectDataArrayBufferCount >= internalEObjectDataArrayBuffer.length)
    {
      InternalEObject [][] newInternalEObjectDataArrayBuffer = new InternalEObject[internalEObjectDataArrayBufferCount * 2][];
      System.arraycopy(internalEObjectDataArrayBuffer, 0, newInternalEObjectDataArrayBuffer, 0, internalEObjectDataArrayBufferCount);
      internalEObjectDataArrayBuffer = newInternalEObjectDataArrayBuffer;
    }
    internalEObjectDataArrayBuffer[internalEObjectDataArrayBufferCount] = values;
  }

  protected URI resolve(URI uri)
  {
    return baseURI != null && uri.isRelative() && uri.hasRelativePath() ? uri.resolve(baseURI) : uri;
  }
}