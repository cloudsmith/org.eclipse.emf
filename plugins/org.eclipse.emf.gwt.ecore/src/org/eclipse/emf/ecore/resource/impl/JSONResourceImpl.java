/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.resource.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.JSONWrapper;
import org.eclipse.emf.ecore.resource.JSONWrapper.JArray;
import org.eclipse.emf.ecore.resource.JSONWrapper.JBoolean;
import org.eclipse.emf.ecore.resource.JSONWrapper.JNull;
import org.eclipse.emf.ecore.resource.JSONWrapper.JNumber;
import org.eclipse.emf.ecore.resource.JSONWrapper.JObject;
import org.eclipse.emf.ecore.resource.JSONWrapper.JString;
import org.eclipse.emf.ecore.resource.JSONWrapper.JValue;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.server.JSONServerWrapper;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import org.eclipse.emf.ecore.util.InternalEList;

import com.google.gwt.core.client.GWT;

/**
 * An Resource for efficiently producing and consuming a compact JSON serialization that's suitable for long term storage.
 */
public class JSONResourceImpl extends ResourceImpl {
	static class JSONIterator implements Iterator<JValue> {

		private final JArray jsonArray;

		private int index;

		JSONIterator(JArray jsonArray) {
			this.jsonArray = jsonArray;
			this.index = -1;
		}

		@Override
		public boolean hasNext() {
			return index + 1 < jsonArray.size();
		}

		@Override
		public JValue next() {
			return jsonArray.get(++index);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public static class JSONReader extends BinaryIO {
		protected static class EClassData {
			public EClass eClass;

			public EFactory eFactory;

			public EStructuralFeatureData[] eStructuralFeatureData;

		}

		protected static class EPackageData {
			public EPackage ePackage;

			public EClassData[] eClassData;

			public final int allocateEClassID() {
				for(int i = 0, length = eClassData.length; i < length; ++i) {
					EClassData eClassData = this.eClassData[i];
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

		private List<EPackageData> ePackageDataList = new ArrayList<EPackageData>();

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

		protected EStructuralFeatureData getEStructuralFeatureData(EClassData eClassData, int featureID,
				JSONIterator json) {
			EStructuralFeatureData eStructuralFeatureData = eClassData.eStructuralFeatureData[featureID];
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
			return str == null || str.isEmpty()
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

		protected EClassData readEClass(JSONIterator json) {
			JSONIterator jsonLocal = new JSONIterator((JArray) json.next());
			EPackageData ePackageData = readEPackage(jsonLocal);
			int id = readInt(jsonLocal);
			EClassData eClassData = ePackageData.eClassData[id];
			if(eClassData == null) {
				eClassData = ePackageData.eClassData[id] = new EClassData();
				String name = readString(jsonLocal);
				eClassData.eClass = (EClass) ePackageData.ePackage.getEClassifier(name);
				eClassData.eFactory = ePackageData.ePackage.getEFactoryInstance();
				eClassData.eStructuralFeatureData = new EStructuralFeatureData[eClassData.eClass.getFeatureCount()];
			}
			return eClassData;
		}

		public InternalEObject readEObject(JSONIterator json) {
			JValue value = json.next();
			if(value instanceof JArray) {
				JSONIterator jsonLocal = new JSONIterator((JArray) value);
				jsonLocal.next(); // Skip id, it's equal to the eObjectList.size()
				EClassData eClassData = readEClass(jsonLocal);
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
						EStructuralFeatureData eStructuralFeatureData = getEStructuralFeatureData(
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

		protected EPackageData readEPackage(JSONIterator json) {
			JValue value = json.next();
			if(value instanceof JArray) {
				JSONIterator jsonLocal = new JSONIterator((JArray) value);
				jsonLocal.next(); // Skip id, it's equal to ePackageDataList.size()
				EPackageData ePackageData = new EPackageData();
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
				ePackageData.eClassData = new EClassData[ePackageData.ePackage.getEClassifiers().size()];
				ePackageDataList.add(ePackageData);
				return ePackageData;
			}

			int id = (int) ((JNumber) value).doubleValue();
			return ePackageDataList.get(id);
		}

		protected EStructuralFeatureData readEStructuralFeature(JSONIterator json) {
			JSONIterator jsonLocal = new JSONIterator((JArray) json.next());
			EClassData eClassData = readEClass(jsonLocal);
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
			EStructuralFeatureData eStructuralFeatureData = readEStructuralFeature(jsonLocal);
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

		protected void readFeatureValue(InternalEObject internalEObject, EStructuralFeatureData eStructuralFeatureData,
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

	public static class JSONWriter extends BinaryIO {
		enum Check {
			NOTHING, DIRECT_RESOURCE, RESOURCE, CONTAINER
		}

		static class EClassData {
			int ePackageID;

			int id;

			EStructuralFeatureData[] eStructuralFeatureData;
		}

		static class EPackageData {
			int id;

			EClassData[] eClassData;

			final int allocateEClassID() {
				for(int i = 0, length = eClassData.length; i < length; ++i) {
					EClassData eClassData = this.eClassData[i];
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

		private final Map<EPackage, EPackageData> ePackageDataMap = new HashMap<EPackage, EPackageData>();

		private final Map<EClass, EClassData> eClassDataMap = new HashMap<EClass, EClassData>();

		private final Map<EObject, Integer> eObjectIDMap = new HashMap<EObject, Integer>();

		private final Map<URI, Integer> uriToIDMap = new HashMap<URI, Integer>();

		private final JSONWrapper wrapper;

		public JSONWriter(JSONWrapper wrapper, Map<?, ?> options) {
			this(wrapper, options, options != null && options.containsKey(BinaryIO.OPTION_VERSION)
					? (Version) options.get(BinaryIO.OPTION_VERSION)
					: Version.VERSION_1_1);
		}

		public JSONWriter(JSONWrapper wrapper, Map<?, ?> options, Version version) {
			this(wrapper, options, version, version.ordinal() > 0
					? getStyle(options)
					: STYLE_BINARY_FLOATING_POINT);
		}

		/**
		 * @since 2.7
		 */
		public JSONWriter(JSONWrapper wrapper, Map<?, ?> options, Version version, int style) {
			this.options = options;
			this.version = version;
			this.style = style;
			this.wrapper = wrapper;
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
			JObject jsonObject = wrapper.createObject();
			jsonObject.put("emf", wrapper.createString(version.toString()));
			if(version.ordinal() > 0)
				jsonObject.put("style", wrapper.createNumber(style));
			jsonObject.put("resource", saveResource(resource));
			return jsonObject;
		}

		private JBoolean writeBoolean(boolean value) {
			return wrapper.createBoolean(value);
		}

		private JNumber writeByte(byte value) {
			return wrapper.createNumber(value);
		}

		private JValue writeChar(char value) {
			return wrapper.createString(new String(new char[] { value }));
		}

		private JValue writeDate(Date date) {
			return wrapper.createNumber(date.getTime());
		}

		private JValue writeDouble(double value) {
			return wrapper.createNumber(value);
		}

		private EClassData writeEClass(EClass eClass, JArray jsonValues) {
			JArray jsonLocalValues = wrapper.createArray();
			EClassData eClassData = eClassDataMap.get(eClass);
			if(eClassData == null) {
				eClassData = new EClassData();
				EPackageData ePackageData = writeEPackage(eClass.getEPackage(), jsonLocalValues);
				eClassData.ePackageID = ePackageData.id;
				jsonLocalValues.add(writeInt(eClassData.id = ePackageData.allocateEClassID()));
				jsonLocalValues.add(writeString(eClass.getName()));
				int featureCount = eClass.getFeatureCount();
				EStructuralFeatureData[] eStructuralFeaturesData = eClassData.eStructuralFeatureData = new EStructuralFeatureData[featureCount];
				for(int i = 0; i < featureCount; ++i) {
					EStructuralFeatureData eStructuralFeatureData = eStructuralFeaturesData[i] = new EStructuralFeatureData();
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

		private JValue writeEObject(InternalEObject internalEObject, Check check) {
			if(internalEObject == null)
				return writeInt(-1);

			Integer id = eObjectIDMap.get(internalEObject);
			if(id != null)
				return writeInt(id);

			JArray jsonValues = wrapper.createArray();
			int idValue = eObjectIDMap.size();
			jsonValues.add(writeInt(idValue));
			eObjectIDMap.put(internalEObject, idValue);
			EClass eClass = internalEObject.eClass();
			EClassData eClassData = writeEClass(eClass, jsonValues);
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
			EStructuralFeatureData[] eStructuralFeatureData = eClassData.eStructuralFeatureData;
			for(int i = 0, length = eStructuralFeatureData.length; i < length; ++i) {
				EStructuralFeatureData structuralFeatureData = eStructuralFeatureData[i];
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

		private JArray writeEObjects(InternalEList<? extends InternalEObject> internalEObjects, Check check) {
			int size = internalEObjects.size();
			InternalEObject[] values = allocateInternalEObjectArray(size);
			internalEObjects.basicToArray(values);

			JArray jsonArray = wrapper.createArray();
			for(int i = 0; i < size; ++i)
				jsonArray.add(writeEObject(values[i], check));
			recycle(values);
			return jsonArray;
		}

		private EPackageData writeEPackage(EPackage ePackage, JArray jsonValues) {
			EPackageData ePackageData = ePackageDataMap.get(ePackage);
			if(ePackageData == null) {
				JArray jsonLocalValues = wrapper.createArray();
				ePackageData = new EPackageData();
				int id = ePackageDataMap.size();
				ePackageData.id = id;
				ePackageData.eClassData = new EClassData[ePackage.getEClassifiers().size()];
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

		private EStructuralFeatureData writeEStructuralFeature(EStructuralFeature eStructuralFeature, JArray jsonValues) {
			JArray jsonLocalValues = wrapper.createArray();
			EClass eClass = eStructuralFeature.getEContainingClass();
			EClassData eClassData = writeEClass(eClass, jsonLocalValues);
			int featureID = eClass.getFeatureID(eStructuralFeature);
			EStructuralFeatureData eStructuralFeatureData = eClassData.eStructuralFeatureData[featureID];
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
			JArray jsonArray = wrapper.createArray();
			for(int i = 0; i < size; ++i) {
				FeatureMap.Entry.Internal entry = values[i];
				jsonArray.add(writeFeatureMapEntry(entry));
			}
			recycle(values);
			return jsonArray;
		}

		private JValue writeFeatureMapEntry(FeatureMap.Entry.Internal entry) {
			JArray jsonValues = wrapper.createArray();
			EStructuralFeatureData eStructuralFeatureData = writeEStructuralFeature(
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
				EStructuralFeatureData eStructuralFeatureData) {

			JArray jsonValues = wrapper.createArray();
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
				default: {
					List<?> dataValues = (List<?>) value;
					int length = dataValues.size();
					JArray jsonArray = wrapper.createArray();
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
			return wrapper.createNumber(value);
		}

		private JNumber writeInt(int value) {
			return wrapper.createNumber(value);
		}

		private JNumber writeLong(long value) {
			return wrapper.createNumber(value);
		}

		private JNumber writeShort(short value) {
			return wrapper.createNumber(value);
		}

		private JValue writeString(String value) {
			return value == null
					? wrapper.createNull()
					: wrapper.createString(value);
		}

		private JValue writeURI(URI uri) {
			return writeURI(uri.trimFragment(), uri.fragment());
		}

		private JValue writeURI(URI uri, String fragment) {
			if(uri == null)
				return wrapper.createNull();

			JArray jsonValues = wrapper.createArray();
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

	private static final JSONWrapper wrapper = GWT.create(JSONWrapper.class);

	public JSONResourceImpl(URI uri) {
		super(uri);
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		if(inputStream instanceof URIConverter.Loadable) {
			((URIConverter.Loadable) inputStream).loadResource(this);
		}
		else {
			ByteArrayOutputStream bld = new ByteArrayOutputStream();
			byte buf[] = new byte[2048];
			int cnt;
			while((cnt = inputStream.read(buf)) > 0)
				bld.write(buf, 0, cnt);
			doLoad(new String(bld.toByteArray(), "UTF-8"), options);
		}
	}

	protected void doLoad(String jsonValue, Map<?, ?> options) throws IOException {
		JSONReader jsonReader = new JSONReader(options);
		jsonReader.fromJSON(wrapper.parse(jsonValue), this);
	}

	protected String doSave(Map<?, ?> options) throws IOException {
		JSONWriter jsonWriter = new JSONWriter(wrapper, options);
		JValue json = jsonWriter.toJSON(this);
		return wrapper.serialize(json);
	}

	@Override
	protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
		if(outputStream instanceof URIConverter.Saveable) {
			((URIConverter.Saveable) outputStream).saveResource(this);
		}
		else {
			outputStream.write(doSave(options).getBytes("UTF-8"));
		}
	}

	public final void fromJson(String jsonString, Map<?, ?> options) throws IOException {
		if(isLoaded)
			return;

		Notification notification = setLoaded(true);
		isLoading = true;

		if(errors != null)
			errors.clear();

		if(warnings != null)
			warnings.clear();

		try {
			doLoad(jsonString, mergeMaps(options, defaultLoadOptions));
		}
		finally {
			isLoading = false;

			if(notification != null) {
				eNotify(notification);
			}

			setModified(false);
		}
	}

	/**
	 * Saves the resource to the output stream using the specified options.
	 * <p>
	 * This implementation is <code>final</code>; clients should override {@link #doSave doSave}.
	 * </p>
	 * 
	 * @param options
	 *            the save options.
	 * @see #save(Map)
	 * @see #doSave(OutputStream, Map)
	 * @see #load(InputStream, Map)
	 */
	@Override
	public final String toJson(Map<?, ?> options) throws IOException {
		if(errors != null)
			errors.clear();

		if(warnings != null)
			warnings.clear();

		String json = doSave(mergeMaps(options, defaultSaveOptions));
		setModified(false);
		return json;
	}
}
