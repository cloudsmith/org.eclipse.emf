package org.eclipse.emf.ecore.json;

import static org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl.OPTION_VERSION;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.json.JSONWrapper.JArray;
import org.eclipse.emf.ecore.json.JSONWrapper.JBoolean;
import org.eclipse.emf.ecore.json.JSONWrapper.JNumber;
import org.eclipse.emf.ecore.json.JSONWrapper.JObject;
import org.eclipse.emf.ecore.json.JSONWrapper.JValue;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Internal;
import org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl.BinaryIO;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.InternalEList;

public class JSONWriter extends BinaryIO {
	enum Check {
		NOTHING, DIRECT_RESOURCE, RESOURCE, CONTAINER
	}

	static class EClassData {
		int ePackageID;

		int id;

		JSONWriter.EStructuralFeatureData[] eStructuralFeatureData;
	}

	static class EPackageData {
		int id;

		JSONWriter.EClassData[] eClassData;

		final int allocateEClassID() {
			for(int i = 0, length = eClassData.length; i < length; ++i) {
				JSONWriter.EClassData eClassData = this.eClassData[i];
				if(eClassData == null) {
					return i;
				}
			}
			return -1;
		}
	}

	static class EStructuralFeatureData {
		String name;

		boolean isTransient;

		/**
		 * @since 2.7
		 */
		boolean isProxyTransient;

		FeatureKind kind;

		EFactory eFactory;

		EDataType eDataType;
	}

	private final Map<EPackage, JSONWriter.EPackageData> ePackageDataMap = new HashMap<EPackage, JSONWriter.EPackageData>();

	private final Map<EClass, JSONWriter.EClassData> eClassDataMap = new HashMap<EClass, JSONWriter.EClassData>();

	private final Map<EObject, Integer> eObjectIDMap = new HashMap<EObject, Integer>();

	private final Map<URI, Integer> uriToIDMap = new HashMap<URI, Integer>();

	public JSONWriter(Map<?, ?> options) {
		this(options, options != null && options.containsKey(OPTION_VERSION)
				? (Version) options.get(OPTION_VERSION)
				: Version.VERSION_1_1);
	}

	public JSONWriter(Map<?, ?> options, Version version) {
		this(options, version, version.ordinal() > 0
				? getStyle(options)
				: STYLE_BINARY_FLOATING_POINT);
	}

	/**
	 * @since 2.7
	 */
	public JSONWriter(Map<?, ?> options, Version version, int style) {
		this.options = options;
		this.version = version;
		this.style = style;
	}

	private JArray saveResource(Resource resource) {
		this.resource = resource;
		URI uri = resource.getURI();
		if(uri != null && uri.isHierarchical() && !uri.isRelative())
			baseURI = uri;

		@SuppressWarnings("unchecked")
		InternalEList<? extends InternalEObject> internalEList = (InternalEList<? extends InternalEObject>) (InternalEList<?>) resource.getContents();
		return writeEObjects(internalEList, Check.CONTAINER);
	}

	public JValue toJSON(Resource resource) {
		JObject jsonObject = JSONResourceImpl.wrapper.createObject();
		jsonObject.put("emf", JSONResourceImpl.wrapper.createString(version.toString()));
		if(version.ordinal() > 0)
			jsonObject.put("style", JSONResourceImpl.wrapper.createNumber(style));
		jsonObject.put("resource", saveResource(resource));
		return jsonObject;
	}

	private JBoolean writeBoolean(boolean value) {
		return JSONResourceImpl.wrapper.createBoolean(value);
	}

	private JNumber writeByte(byte value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JValue writeChar(char value) {
		return JSONResourceImpl.wrapper.createString(new String(new char[] { value }));
	}

	private JValue writeDate(Date date) {
		return JSONResourceImpl.wrapper.createNumber(date.getTime());
	}

	private JValue writeDouble(double value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JSONWriter.EClassData writeEClass(EClass eClass, JArray jsonValues) {
		JArray jsonLocalValues = JSONResourceImpl.wrapper.createArray();
		JSONWriter.EClassData eClassData = eClassDataMap.get(eClass);
		if(eClassData == null) {
			eClassData = new EClassData();
			JSONWriter.EPackageData ePackageData = writeEPackage(eClass.getEPackage(), jsonLocalValues);
			eClassData.ePackageID = ePackageData.id;
			jsonLocalValues.add(writeInt(eClassData.id = ePackageData.allocateEClassID()));
			jsonLocalValues.add(writeString(eClass.getName()));
			int featureCount = eClass.getFeatureCount();
			JSONWriter.EStructuralFeatureData[] eStructuralFeaturesData = eClassData.eStructuralFeatureData = new JSONWriter.EStructuralFeatureData[featureCount];
			for(int i = 0; i < featureCount; ++i) {
				JSONWriter.EStructuralFeatureData eStructuralFeatureData = eStructuralFeaturesData[i] = new EStructuralFeatureData();
				EStructuralFeature.Internal eStructuralFeature = (EStructuralFeature.Internal) eClass.getEStructuralFeature(i);
				eStructuralFeatureData.name = eStructuralFeature.getName();
				eStructuralFeatureData.isTransient = eStructuralFeature.isTransient() ||
						eStructuralFeature.isContainer() && !eStructuralFeature.isResolveProxies();
				eStructuralFeatureData.kind = FeatureKind.get(eStructuralFeature);
				if(eStructuralFeature instanceof EAttribute) {
					EAttribute eAttribute = (EAttribute) eStructuralFeature;
					EDataType eDataType = eAttribute.getEAttributeType();
					eStructuralFeatureData.eDataType = eDataType;
					eStructuralFeatureData.eFactory = eDataType.getEPackage().getEFactoryInstance();
					eStructuralFeatureData.isProxyTransient = eStructuralFeatureData.kind == FeatureKind.FEATURE_MAP;
				}
				else {
					eStructuralFeatureData.isProxyTransient = true;
				}
			}
			ePackageData.eClassData[eClassData.id] = eClassData;
			eClassDataMap.put(eClass, eClassData);
		}
		else {
			jsonLocalValues.add(writeInt(eClassData.ePackageID));
			jsonLocalValues.add(writeInt(eClassData.id));
		}
		jsonValues.add(jsonLocalValues);
		return eClassData;
	}

	private JValue writeEObject(InternalEObject internalEObject, JSONWriter.Check check) {
		if(internalEObject == null)
			return writeInt(-1);

		Integer id = eObjectIDMap.get(internalEObject);
		if(id != null)
			return writeInt(id);

		JArray jsonValues = JSONResourceImpl.wrapper.createArray();
		int idValue = eObjectIDMap.size();
		jsonValues.add(writeInt(idValue));
		eObjectIDMap.put(internalEObject, idValue);
		EClass eClass = internalEObject.eClass();
		JSONWriter.EClassData eClassData = writeEClass(eClass, jsonValues);
		boolean checkIsTransientProxy = false;
		switch(check) {
			case DIRECT_RESOURCE: {
				Internal resource = internalEObject.eDirectResource();
				if(resource != null) {
					jsonValues.add(writeInt(-1));
					jsonValues.add(writeURI(resource.getURI(), resource.getURIFragment(internalEObject)));
					if((style & STYLE_PROXY_ATTRIBUTES) == 0) {
						return jsonValues;
					}
					checkIsTransientProxy = true;
				}
				else if(internalEObject.eIsProxy()) {
					jsonValues.add(writeInt(-1));
					jsonValues.add(writeURI(internalEObject.eProxyURI()));
					if((style & STYLE_PROXY_ATTRIBUTES) == 0) {
						return jsonValues;
					}
					checkIsTransientProxy = true;
				}
				break;
			}
			case RESOURCE: {
				Resource resource = internalEObject.eResource();
				if(resource != this.resource && resource != null) {
					jsonValues.add(writeInt(-1));
					jsonValues.add(writeURI(resource.getURI(), resource.getURIFragment(internalEObject)));
					if((style & STYLE_PROXY_ATTRIBUTES) == 0) {
						return jsonValues;
					}
					checkIsTransientProxy = true;
				}
				else if(internalEObject.eIsProxy()) {
					jsonValues.add(writeInt(-1));
					jsonValues.add(writeURI(internalEObject.eProxyURI()));
					if((style & STYLE_PROXY_ATTRIBUTES) == 0) {
						return jsonValues;
					}
					checkIsTransientProxy = true;
				}
				break;
			}
			case NOTHING:
			case CONTAINER: {
				break;
			}
		}
		JSONWriter.EStructuralFeatureData[] eStructuralFeatureData = eClassData.eStructuralFeatureData;
		for(int i = 0, length = eStructuralFeatureData.length; i < length; ++i) {
			JSONWriter.EStructuralFeatureData structuralFeatureData = eStructuralFeatureData[i];
			if(!structuralFeatureData.isTransient &&
					(structuralFeatureData.kind != FeatureKind.EOBJECT_CONTAINER_PROXY_RESOLVING || check == Check.CONTAINER) &&
					(!checkIsTransientProxy || !structuralFeatureData.isProxyTransient)) {
				if(internalEObject.eIsSet(i)) {
					jsonValues.add(writeInt(i));
					jsonValues.add(writeFeatureValue(internalEObject, i, structuralFeatureData));
				}
			}
		}
		return jsonValues;
	}

	private JArray writeEObjects(InternalEList<? extends InternalEObject> internalEObjects, JSONWriter.Check check) {
		int size = internalEObjects.size();
		InternalEObject[] values = allocateInternalEObjectArray(size);
		internalEObjects.basicToArray(values);

		JArray jsonArray = JSONResourceImpl.wrapper.createArray();
		for(int i = 0; i < size; ++i)
			jsonArray.add(writeEObject(values[i], check));
		recycle(values);
		return jsonArray;
	}

	private JSONWriter.EPackageData writeEPackage(EPackage ePackage, JArray jsonValues) {
		JSONWriter.EPackageData ePackageData = ePackageDataMap.get(ePackage);
		if(ePackageData == null) {
			JArray jsonLocalValues = JSONResourceImpl.wrapper.createArray();
			ePackageData = new EPackageData();
			int id = ePackageDataMap.size();
			ePackageData.id = id;
			ePackageData.eClassData = new JSONWriter.EClassData[ePackage.getEClassifiers().size()];
			jsonLocalValues.add(writeInt(id));
			jsonLocalValues.add(writeString(ePackage.getNsURI()));
			jsonLocalValues.add(writeURI(EcoreUtil.getURI(ePackage)));
			ePackageDataMap.put(ePackage, ePackageData);
			jsonValues.add(jsonLocalValues);
		}
		else {
			jsonValues.add(writeInt(ePackageData.id));
		}
		return ePackageData;
	}

	private JSONWriter.EStructuralFeatureData writeEStructuralFeature(EStructuralFeature eStructuralFeature, JArray jsonValues) {
		JArray jsonLocalValues = JSONResourceImpl.wrapper.createArray();
		EClass eClass = eStructuralFeature.getEContainingClass();
		JSONWriter.EClassData eClassData = writeEClass(eClass, jsonLocalValues);
		int featureID = eClass.getFeatureID(eStructuralFeature);
		JSONWriter.EStructuralFeatureData eStructuralFeatureData = eClassData.eStructuralFeatureData[featureID];
		jsonLocalValues.add(writeInt(featureID));
		if(eStructuralFeatureData.name != null) {
			jsonLocalValues.add(writeString(eStructuralFeatureData.name));
			eStructuralFeatureData.name = null;
		}
		jsonValues.add(jsonLocalValues);
		return eStructuralFeatureData;
	}

	private JValue writeFeatureMap(FeatureMap.Internal featureMap) {
		int size = featureMap.size();
		FeatureMap.Entry.Internal[] values = allocateFeatureMapEntryArray(size);
		featureMap.toArray(values);
		JArray jsonArray = JSONResourceImpl.wrapper.createArray();
		for(int i = 0; i < size; ++i) {
			FeatureMap.Entry.Internal entry = values[i];
			jsonArray.add(writeFeatureMapEntry(entry));
		}
		recycle(values);
		return jsonArray;
	}

	private JValue writeFeatureMapEntry(FeatureMap.Entry.Internal entry) {
		JArray jsonValues = JSONResourceImpl.wrapper.createArray();
		JSONWriter.EStructuralFeatureData eStructuralFeatureData = writeEStructuralFeature(
			entry.getEStructuralFeature(), jsonValues);
		Object value = entry.getValue();
		switch(eStructuralFeatureData.kind) {
			case EOBJECT:
			case EOBJECT_LIST:
			case EOBJECT_CONTAINMENT:
			case EOBJECT_CONTAINMENT_LIST:
				jsonValues.add(writeEObject((InternalEObject) value, Check.NOTHING));
				break;
			case EOBJECT_CONTAINMENT_PROXY_RESOLVING:
			case EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING:
				jsonValues.add(writeEObject((InternalEObject) value, Check.DIRECT_RESOURCE));
				break;
			case EOBJECT_PROXY_RESOLVING:
			case EOBJECT_LIST_PROXY_RESOLVING:
				jsonValues.add(writeEObject((InternalEObject) value, Check.RESOURCE));
				break;
			case BOOLEAN:
				jsonValues.add(writeBoolean((Boolean) value));
				break;
			case BYTE:
				jsonValues.add(writeByte((Byte) value));
				break;
			case CHAR:
				jsonValues.add(writeChar((Character) value));
				break;
			case DOUBLE:
				jsonValues.add(writeDouble((Double) value));
				break;
			case FLOAT:
				jsonValues.add(writeFloat((Float) value));
				break;
			case INT:
				jsonValues.add(writeInt((Integer) value));
				break;
			case LONG:
				jsonValues.add(writeLong((Long) value));
				break;
			case SHORT:
				jsonValues.add(writeShort((Short) value));
				break;
			case STRING:
				jsonValues.add(writeString((String) value));
				break;
			case DATE:
				jsonValues.add(writeDate((Date) value));
				break;
			default: {
				String literal = eStructuralFeatureData.eFactory.convertToString(
					eStructuralFeatureData.eDataType, value);
				jsonValues.add(writeString(literal));
				break;
			}
		}
		return jsonValues;
	}

	private JValue writeFeatureValue(InternalEObject internalEObject, int featureID,
			JSONWriter.EStructuralFeatureData eStructuralFeatureData) {

		JArray jsonValues = JSONResourceImpl.wrapper.createArray();
		if(eStructuralFeatureData.name != null) {
			jsonValues.add(writeString(eStructuralFeatureData.name));
			eStructuralFeatureData.name = null;
		}
		Object value = internalEObject.eGet(featureID, false, true);
		switch(eStructuralFeatureData.kind) {
			case EOBJECT:
			case EOBJECT_CONTAINMENT: {
				jsonValues.add(writeEObject((InternalEObject) value, Check.NOTHING));
				break;
			}
			case EOBJECT_CONTAINMENT_PROXY_RESOLVING: {
				jsonValues.add(writeEObject((InternalEObject) value, Check.DIRECT_RESOURCE));
				break;
			}
			case EOBJECT_CONTAINER_PROXY_RESOLVING:
			case EOBJECT_PROXY_RESOLVING: {
				jsonValues.add(writeEObject((InternalEObject) value, Check.RESOURCE));
				break;
			}
			case EOBJECT_LIST:
			case EOBJECT_CONTAINMENT_LIST: {
				@SuppressWarnings("unchecked")
				InternalEList<? extends InternalEObject> internalEList = (InternalEList<? extends InternalEObject>) value;
				jsonValues.add(writeEObjects(internalEList, Check.NOTHING));
				break;
			}
			case EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING: {
				@SuppressWarnings("unchecked")
				InternalEList<? extends InternalEObject> internalEList = (InternalEList<? extends InternalEObject>) value;
				jsonValues.add(writeEObjects(internalEList, Check.DIRECT_RESOURCE));
				break;
			}
			case EOBJECT_LIST_PROXY_RESOLVING: {
				@SuppressWarnings("unchecked")
				InternalEList<? extends InternalEObject> internalEList = (InternalEList<? extends InternalEObject>) value;
				jsonValues.add(writeEObjects(internalEList, Check.RESOURCE));
				break;
			}
			case BOOLEAN:
				jsonValues.add(writeBoolean((Boolean) value));
				break;
			case BYTE:
				jsonValues.add(writeByte((Byte) value));
				break;
			case CHAR:
				jsonValues.add(writeChar((Character) value));
				break;
			case DOUBLE:
				jsonValues.add(writeDouble((Double) value));
				break;
			case FLOAT:
				jsonValues.add(writeFloat((Float) value));
				break;
			case INT:
				jsonValues.add(writeInt((Integer) value));
				break;
			case LONG:
				jsonValues.add(writeLong((Long) value));
				break;
			case SHORT:
				jsonValues.add(writeShort((Short) value));
				break;
			case STRING:
				jsonValues.add(writeString((String) value));
				break;
			case DATE:
				jsonValues.add(writeDate((Date) value));
				break;
			case FEATURE_MAP:
				jsonValues.add(writeFeatureMap((FeatureMap.Internal) value));
				break;
			case DATA: {
				String literal = eStructuralFeatureData.eFactory.convertToString(
					eStructuralFeatureData.eDataType, value);
				jsonValues.add(writeString(literal));
				break;
			}
			case ENUMERATOR: {
				if((style & STYLE_BINARY_ENUMERATOR) != 0)
					jsonValues.add(writeInt(((Enumerator) value).getValue()));
				else
					jsonValues.add(writeString(eStructuralFeatureData.eFactory.convertToString(
						eStructuralFeatureData.eDataType, value)));
				break;
				}
			default: {
				List<?> dataValues = (List<?>) value;
				int length = dataValues.size();
				JArray jsonArray = JSONResourceImpl.wrapper.createArray();
				for(int j = 0; j < length; ++j) {
					String literal = eStructuralFeatureData.eFactory.convertToString(
						eStructuralFeatureData.eDataType, dataValues.get(j));
					jsonArray.add(writeString(literal));
				}
				jsonValues.add(jsonArray);
			}
		}
		return jsonValues;
	}

	private JNumber writeFloat(float value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JNumber writeInt(int value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JNumber writeLong(long value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JNumber writeShort(short value) {
		return JSONResourceImpl.wrapper.createNumber(value);
	}

	private JValue writeString(String value) {
		return value == null
				? JSONResourceImpl.wrapper.createNull()
				: JSONResourceImpl.wrapper.createString(value);
	}

	private JValue writeURI(URI uri) {
		return writeURI(uri.trimFragment(), uri.fragment());
	}

	private JValue writeURI(URI uri, String fragment) {
		if(uri == null)
			return JSONResourceImpl.wrapper.createNull();

		JArray jsonValues = JSONResourceImpl.wrapper.createArray();
		assert uri.fragment() == null;
		Integer id = uriToIDMap.get(uri);
		if(id == null) {
			int idValue = uriToIDMap.size();
			uriToIDMap.put(uri, idValue);
			jsonValues.add(writeInt(idValue));
			jsonValues.add(writeString(deresolve(uri).toString()));
		}
		else {
			jsonValues.add(writeInt(id));
		}
		jsonValues.add(writeString(fragment));
		return jsonValues;
	}
}