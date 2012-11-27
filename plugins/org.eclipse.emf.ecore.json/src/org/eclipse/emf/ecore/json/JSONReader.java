package org.eclipse.emf.ecore.json;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.json.JSONWrapper.JArray;
import org.eclipse.emf.ecore.json.JSONWrapper.JBoolean;
import org.eclipse.emf.ecore.json.JSONWrapper.JNull;
import org.eclipse.emf.ecore.json.JSONWrapper.JNumber;
import org.eclipse.emf.ecore.json.JSONWrapper.JObject;
import org.eclipse.emf.ecore.json.JSONWrapper.JString;
import org.eclipse.emf.ecore.json.JSONWrapper.JValue;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl.BinaryIO;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import org.eclipse.emf.ecore.util.InternalEList;

public class JSONReader extends BinaryIO {
	protected static class EClassData {
		public EClass eClass;

		public EFactory eFactory;

		public JSONReader.EStructuralFeatureData[] eStructuralFeatureData;

	}

	protected static class EPackageData {
		public EPackage ePackage;

		public JSONReader.EClassData[] eClassData;

		public final int allocateEClassID() {
			for(int i = 0, length = eClassData.length; i < length; ++i) {
				JSONReader.EClassData eClassData = this.eClassData[i];
				if(eClassData == null) {
					return i;
				}
			}
			return -1;
		}
	}

	protected static class EStructuralFeatureData {
		public int featureID;

		public EStructuralFeature eStructuralFeature;

		public FeatureKind kind;

		public EFactory eFactory;

		public EDataType eDataType;
	}

	private ResourceSet resourceSet;

	private List<JSONReader.EPackageData> ePackageDataList = new ArrayList<JSONReader.EPackageData>();

	private List<InternalEObject> eObjectList = new ArrayList<InternalEObject>();

	private List<URI> uriList = new ArrayList<URI>();

	private BasicEList<InternalEObject> internalEObjectList = new BasicEList<InternalEObject>();

	private BasicEList<Object> dataValueList = new BasicEList<Object>();

	private int[][] intDataArrayBuffer = new int[50][];

	private int intDataArrayBufferCount = -1;

	public JSONReader(Map<?, ?> options) {
		this.options = options;
	}

	protected int[] allocateIntArray(int length) {
		if(intDataArrayBufferCount == -1)
			return new int[length];

		int[] buffer = intDataArrayBuffer[intDataArrayBufferCount];
		intDataArrayBuffer[intDataArrayBufferCount--] = null;
		return buffer.length >= length
				? buffer
				: new int[length];
	}

	public void fromJSON(JValue value, Resource resource) {
		if(!(value instanceof JObject))
			throw new IllegalArgumentException("Not a JObject");
		JObject jsonObject = (JObject) value;
		JValue jsonVersion = jsonObject.get("emf");
		if(!(jsonVersion instanceof JString))
			throw new IllegalArgumentException("Not a JSON EMF object");
		version = Version.valueOf(((JString) jsonVersion).stringValue());
		if(version.ordinal() > 0)
			style = (int) ((JNumber) jsonObject.get("style")).doubleValue();

		this.resource = resource;
		loadResource(resource, (JArray) jsonObject.get("resource"));
	}

	protected JSONReader.EStructuralFeatureData getEStructuralFeatureData(JSONReader.EClassData eClassData, int featureID,
			JSONIterator json) {
		JSONReader.EStructuralFeatureData eStructuralFeatureData = eClassData.eStructuralFeatureData[featureID];
		if(eStructuralFeatureData == null) {
			eStructuralFeatureData = eClassData.eStructuralFeatureData[featureID] = new EStructuralFeatureData();
			String name = readString(json);
			eStructuralFeatureData.eStructuralFeature = eClassData.eClass.getEStructuralFeature(name);
			eStructuralFeatureData.featureID = eClassData.eClass.getFeatureID(eStructuralFeatureData.eStructuralFeature);
			eStructuralFeatureData.kind = FeatureKind.get(eStructuralFeatureData.eStructuralFeature);
			if(eStructuralFeatureData.eStructuralFeature instanceof EAttribute) {
				EAttribute eAttribute = (EAttribute) eStructuralFeatureData.eStructuralFeature;
				eStructuralFeatureData.eDataType = eAttribute.getEAttributeType();
				eStructuralFeatureData.eFactory = eStructuralFeatureData.eDataType.getEPackage().getEFactoryInstance();
			}
		}
		return eStructuralFeatureData;
	}

	public void loadResource(Resource resource, JArray jsonArray) {
		this.resource = resource;
		this.resourceSet = resource.getResourceSet();
		URI uri = resource.getURI();
		if(uri != null && uri.isHierarchical() && !uri.isRelative()) {
			baseURI = uri;
		}
		int size = jsonArray.size();
		JSONIterator jsonIter = new JSONIterator(jsonArray);
		InternalEObject[] values = allocateInternalEObjectArray(size);
		for(int i = 0; i < size; ++i) {
			values[i] = readEObject(jsonIter);
		}
		internalEObjectList.setData(size, values);
		@SuppressWarnings("unchecked")
		InternalEList<InternalEObject> internalEObjects = (InternalEList<InternalEObject>) (InternalEList<?>) resource.getContents();
		internalEObjects.addAllUnique(internalEObjectList);
		recycle(values);
	}

	public boolean readBoolean(JSONIterator json) {
		JValue value = json.next();
		return value instanceof JBoolean
				? ((JBoolean) value).booleanValue()
				: false;
	}

	public byte readByte(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? 0
				: val.byteValue();
	}

	public char readChar(JSONIterator json) {
		String str = readString(json);
		return str == null || str.length() == 0
				? 0
				: str.charAt(0);
	}

	public Date readDate(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? null
				: new Date(val.longValue());
	}

	public Double readDouble(JSONIterator json) {
		JValue value = json.next();
		return value instanceof JNumber
				? ((JNumber) value).doubleValue()
				: null;
	}

	protected JSONReader.EClassData readEClass(JSONIterator json) {
		JSONIterator jsonLocal = new JSONIterator((JArray) json.next());
		JSONReader.EPackageData ePackageData = readEPackage(jsonLocal);
		int id = readInt(jsonLocal);
		JSONReader.EClassData eClassData = ePackageData.eClassData[id];
		if(eClassData == null) {
			eClassData = ePackageData.eClassData[id] = new EClassData();
			String name = readString(jsonLocal);
			eClassData.eClass = (EClass) ePackageData.ePackage.getEClassifier(name);
			eClassData.eFactory = ePackageData.ePackage.getEFactoryInstance();
			eClassData.eStructuralFeatureData = new JSONReader.EStructuralFeatureData[eClassData.eClass.getFeatureCount()];
		}
		return eClassData;
	}

	public InternalEObject readEObject(JSONIterator json) {
		JValue value = json.next();
		if(value instanceof JArray) {
			JSONIterator jsonLocal = new JSONIterator((JArray) value);
			jsonLocal.next(); // Skip id, it's equal to the eObjectList.size()
			JSONReader.EClassData eClassData = readEClass(jsonLocal);
			InternalEObject internalEObject = (InternalEObject) eClassData.eFactory.create(eClassData.eClass);
			eObjectList.add(internalEObject);
			while(jsonLocal.hasNext()) {
				int featureID = readInt(jsonLocal);
				if(featureID == -1) {
					internalEObject.eSetProxyURI(readURI(jsonLocal));
					if((style & STYLE_PROXY_ATTRIBUTES) == 0) {
						break;
					}
				}
				else {
					JSONIterator fvIter = new JSONIterator((JArray) jsonLocal.next());
					JSONReader.EStructuralFeatureData eStructuralFeatureData = getEStructuralFeatureData(
						eClassData, featureID, fvIter);
					readFeatureValue(internalEObject, eStructuralFeatureData, fvIter);
				}
			}
			return internalEObject;
		}

		int id = (int) ((JNumber) value).doubleValue();
		return id == -1
				? null
				: eObjectList.get(id);
	}

	public void readEObjects(InternalEList<InternalEObject> internalEObjects, JSONIterator json) {
		// Read all the values into an array.
		//
		JArray jsonArray = (JArray) json.next();
		int size = jsonArray.size();
		JSONIterator arrayItor = new JSONIterator(jsonArray);
		InternalEObject[] values = allocateInternalEObjectArray(size);
		for(int i = 0; i < size; ++i)
			values[i] = readEObject(arrayItor);

		int existingSize = internalEObjects.size();

		// If the list is empty, we need to add all the objects,
		// otherwise, the reference is bidirectional and the list is at least partially populated.
		//
		if(existingSize == 0) {
			internalEObjectList.setData(size, values);
			internalEObjects.addAllUnique(internalEObjectList);
		}
		else {
			InternalEObject[] existingValues = allocateInternalEObjectArray(existingSize);
			internalEObjects.basicToArray(existingValues);
			int[] indices = allocateIntArray(existingSize);
			int duplicateCount = 0;
			LOOP: for(int i = 0; i < size; ++i) {
				InternalEObject internalEObject = values[i];
				for(int j = 0, count = 0; j < existingSize; ++j) {
					InternalEObject existingInternalEObject = existingValues[j];
					if(existingInternalEObject == internalEObject) {
						if(duplicateCount != count) {
							internalEObjects.move(duplicateCount, count);
						}
						indices[duplicateCount] = i;
						++count;
						++duplicateCount;
						existingValues[j] = null;
						continue LOOP;
					}
					else if(existingInternalEObject != null) {
						++count;
					}
				}

				values[i - duplicateCount] = internalEObject;
			}

			size -= existingSize;
			internalEObjectList.setData(size, values);
			internalEObjects.addAllUnique(0, internalEObjectList);
			for(int i = 0; i < existingSize; ++i) {
				int newPosition = indices[i];
				int oldPosition = size + i;
				if(newPosition != oldPosition) {
					internalEObjects.move(newPosition, oldPosition);
				}
			}
			recycle(existingValues);
			recycle(indices);
		}
		recycle(values);
	}

	protected JSONReader.EPackageData readEPackage(JSONIterator json) {
		JValue value = json.next();
		if(value instanceof JArray) {
			JSONIterator jsonLocal = new JSONIterator((JArray) value);
			jsonLocal.next(); // Skip id, it's equal to ePackageDataList.size()
			JSONReader.EPackageData ePackageData = new EPackageData();
			String nsURI = readString(jsonLocal);
			URI uri = readURI(jsonLocal);
			if(resourceSet != null) {
				ePackageData.ePackage = EPackage.Registry.INSTANCE.getEPackage(nsURI);
				if(ePackageData.ePackage == null) {
					ePackageData.ePackage = (EPackage) resourceSet.getEObject(uri, true);
				}
			}
			else {
				ePackageData.ePackage = EPackage.Registry.INSTANCE.getEPackage(nsURI);
			}
			ePackageData.eClassData = new JSONReader.EClassData[ePackageData.ePackage.getEClassifiers().size()];
			ePackageDataList.add(ePackageData);
			return ePackageData;
		}

		int id = (int) ((JNumber) value).doubleValue();
		return ePackageDataList.get(id);
	}

	protected JSONReader.EStructuralFeatureData readEStructuralFeature(JSONIterator json) {
		JSONIterator jsonLocal = new JSONIterator((JArray) json.next());
		JSONReader.EClassData eClassData = readEClass(jsonLocal);
		int featureID = readInt(jsonLocal);
		return getEStructuralFeatureData(eClassData, featureID, jsonLocal);
	}

	public void readFeatureMap(FeatureMap.Internal featureMap, JSONIterator json) {
		// Read all the values into an array.
		//
		JArray jsonArray = (JArray) json.next();
		int size = jsonArray.size();
		JSONIterator jsonArrayItor = new JSONIterator(jsonArray);
		FeatureMap.Entry.Internal[] values = allocateFeatureMapEntryArray(size);
		for(int i = 0; i < size; ++i) {
			values[i] = readFeatureMapEntry(jsonArrayItor);
		}
		int existingSize = featureMap.size();

		// If the list is empty, we need to add all the objects,
		// otherwise, the reference is bidirectional and the list is at least partially populated.
		//
		if(existingSize == 0) {
			featureMap.addAllUnique(values, 0, size);
		}
		else {
			FeatureMap.Entry.Internal[] existingValues = allocateFeatureMapEntryArray(existingSize);
			featureMap.basicToArray(existingValues);
			int[] indices = allocateIntArray(existingSize);
			int duplicateCount = 0;
			LOOP: for(int i = 0; i < size; ++i) {
				FeatureMap.Entry.Internal entry = values[i];
				for(int j = 0, count = 0; j < existingSize; ++j) {
					FeatureMap.Entry.Internal existingEntry = existingValues[j];
					if(entry.equals(existingEntry)) {
						if(duplicateCount != count) {
							featureMap.move(duplicateCount, count);
						}
						indices[duplicateCount] = i;
						++count;
						++duplicateCount;
						existingValues[j] = null;
						continue LOOP;
					}
					else if(existingEntry != null) {
						++count;
					}
				}

				values[i - duplicateCount] = entry;
			}

			size -= existingSize;
			internalEObjectList.setData(size, values);
			featureMap.addAllUnique(0, values, 0, size);
			for(int i = 0; i < existingSize; ++i) {
				int newPosition = indices[i];
				int oldPosition = size + i;
				if(newPosition != oldPosition) {
					featureMap.move(newPosition, oldPosition);
				}
			}
			recycle(existingValues);
			recycle(indices);
		}
		recycle(values);
	}

	public FeatureMap.Entry.Internal readFeatureMapEntry(JSONIterator json) {
		JSONIterator jsonLocal = new JSONIterator((JArray) json.next());
		JSONReader.EStructuralFeatureData eStructuralFeatureData = readEStructuralFeature(jsonLocal);
		Object value;
		switch(eStructuralFeatureData.kind) {
			case EOBJECT_CONTAINER:
			case EOBJECT_CONTAINER_PROXY_RESOLVING:
			case EOBJECT:
			case EOBJECT_LIST:
			case EOBJECT_PROXY_RESOLVING:
			case EOBJECT_LIST_PROXY_RESOLVING:
			case EOBJECT_CONTAINMENT:
			case EOBJECT_CONTAINMENT_LIST:
			case EOBJECT_CONTAINMENT_PROXY_RESOLVING:
			case EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING: {
				value = readEObject(jsonLocal);
				break;
			}
			case STRING: {
				value = readString(jsonLocal);
				break;
			}
			case DATE: {
				value = readDate(jsonLocal);
				break;
			}
			case BOOLEAN: {
				value = readBoolean(jsonLocal);
				break;
			}
			case BYTE: {
				value = readByte(jsonLocal);
				break;
			}
			case CHAR: {
				value = readChar(jsonLocal);
				break;
			}
			case DOUBLE: {
				value = readDouble(jsonLocal);
				break;
			}
			case FLOAT: {
				value = readFloat(jsonLocal);
				break;
			}
			case INT: {
				value = readInt(jsonLocal);
				break;
			}
			case LONG: {
				value = readLong(jsonLocal);
				break;
			}
			case SHORT: {
				value = readShort(jsonLocal);
				break;
			}
			default: {
				String literal = readString(jsonLocal);
				value = eStructuralFeatureData.eFactory.createFromString(eStructuralFeatureData.eDataType, literal);
				break;
			}
		}
		return FeatureMapUtil.createRawEntry(eStructuralFeatureData.eStructuralFeature, value);
	}

	protected void readFeatureValue(InternalEObject internalEObject, JSONReader.EStructuralFeatureData eStructuralFeatureData,
			JSONIterator json) {
		switch(eStructuralFeatureData.kind) {
			case EOBJECT_CONTAINER:
			case EOBJECT_CONTAINER_PROXY_RESOLVING:
			case EOBJECT:
			case EOBJECT_PROXY_RESOLVING:
			case EOBJECT_CONTAINMENT:
			case EOBJECT_CONTAINMENT_PROXY_RESOLVING: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readEObject(json));
				break;
			}
			case EOBJECT_LIST:
			case EOBJECT_LIST_PROXY_RESOLVING:
			case EOBJECT_CONTAINMENT_LIST:
			case EOBJECT_CONTAINMENT_LIST_PROXY_RESOLVING: {
				@SuppressWarnings("unchecked")
				InternalEList<InternalEObject> internalEList = (InternalEList<InternalEObject>) internalEObject.eGet(
					eStructuralFeatureData.featureID, false, true);
				readEObjects(internalEList, json);
				break;
			}
			case STRING: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readString(json));
				break;
			}
			case FEATURE_MAP: {
				FeatureMap.Internal featureMap = (FeatureMap.Internal) internalEObject.eGet(
					eStructuralFeatureData.featureID, false, true);
				readFeatureMap(featureMap, json);
				break;
			}
			case DATE: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readDate(json));
				break;
			}
			case DATA: {
				String literal = readString(json);
				internalEObject.eSet(
					eStructuralFeatureData.featureID,
					eStructuralFeatureData.eFactory.createFromString(eStructuralFeatureData.eDataType, literal));
				break;
			}
			case BOOLEAN: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readBoolean(json));
				break;
			}
			case BYTE: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readByte(json));
				break;
			}
			case CHAR: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readChar(json));
				break;
			}
			case DOUBLE: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readDouble(json));
				break;
			}
			case FLOAT: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readFloat(json));
				break;
			}
			case INT: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readInt(json));
				break;
			}
			case LONG: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readLong(json));
				break;
			}
			case SHORT: {
				internalEObject.eSet(eStructuralFeatureData.featureID, readShort(json));
				break;
			}
			case ENUMERATOR: {
				internalEObject.eSet(eStructuralFeatureData.featureID, (style & STYLE_BINARY_ENUMERATOR) != 0
          ? ((EEnum)eStructuralFeatureData.eDataType).getEEnumLiteral(readInt(json)).getInstance()
          : eStructuralFeatureData.eFactory.createFromString(eStructuralFeatureData.eDataType, readString(json)));
				break;
			}
			default: {
				JArray jsonArray = (JArray) json.next();
				int size = jsonArray.size();
				JSONIterator jsonArrayIter = new JSONIterator(jsonArray);
				dataValueList.grow(size);
				Object[] dataValues = dataValueList.data();
				for(int i = 0; i < size; ++i) {
					String literal = readString(jsonArrayIter);
					dataValues[i] = eStructuralFeatureData.eFactory.createFromString(
						eStructuralFeatureData.eDataType, literal);
				}
				dataValueList.setData(size, dataValues);
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) internalEObject.eGet(
					eStructuralFeatureData.featureID, false, true);
				values.addAll(dataValueList);
				break;
			}
		}
	}

	public float readFloat(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? 0
				: val.floatValue();
	}

	public int readInt(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? 0
				: val.intValue();
	}

	public long readLong(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? 0
				: val.longValue();
	}

	public short readShort(JSONIterator json) {
		Double val = readDouble(json);
		return val == null
				? 0
				: val.shortValue();
	}

	public String readString(JSONIterator json) {
		JValue value = json.next();
		return value instanceof JString
				? ((JString) value).stringValue()
				: null;
	}

	public URI readURI(JSONIterator json) {
		JValue nxt = json.next();
		if(nxt instanceof JNull)
			return null;

		JSONIterator jsonLocal = new JSONIterator((JArray) nxt);
		int id = readInt(jsonLocal);
		URI uri;
		if(uriList.size() <= id) {
			String value = readString(jsonLocal);
			uri = resolve(URI.createURI(value));
			uriList.add(uri);
		}
		else {
			uri = uriList.get(id);
		}
		String fragment = readString(jsonLocal);
		if(fragment != null) {
			uri = uri.appendFragment(fragment);
		}
		return uri;
	}

	protected void recycle(int[] values) {
		if(++intDataArrayBufferCount >= intDataArrayBuffer.length) {
			int[][] newIntDataArrayBuffer = new int[intDataArrayBufferCount * 2][];
			System.arraycopy(intDataArrayBuffer, 0, newIntDataArrayBuffer, 0, intDataArrayBufferCount);
			intDataArrayBuffer = newIntDataArrayBuffer;
		}
		intDataArrayBuffer[intDataArrayBufferCount] = values;
	}
}