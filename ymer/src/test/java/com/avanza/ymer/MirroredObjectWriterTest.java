/*
 * Copyright 2015 Avanza Bank AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.avanza.ymer;

import com.avanza.ymer.YmerInitialLoadIntegrationTest.TestSpaceObjectV1Patch;
import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.sync.DataSyncOperation;
import com.gigaspaces.sync.DataSyncOperationType;
import com.gigaspaces.sync.OperationsBatchData;
import com.gigaspaces.sync.SynchronizationSourceDetails;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class MirroredObjectWriterTest {

	private MirroredObjectWriter mirroredObjectWriter;
	private DocumentConverter documentConverter;
	private DocumentDb documentDb;
	private MirroredObject<TestSpaceObject> mirroredObject;
	private MirroredObject<TestSpaceOtherObject> anotherMirroredDocument;
	private MirroredObject<TestReloadableSpaceObject> mirroredReloadableDocument;
	private SpaceMirrorContext mirror;
	private MirrorExceptionSpy mirrorExceptionSpy;
	private MirroredObjects mirroredObjects;

	@Before
	public void setup() {
		DocumentPatch[] patches = { new TestSpaceObjectV1Patch() };
		mirroredObject = MirroredObjectDefinition.create(TestSpaceObject.class).documentPatches(patches).buildMirroredDocument(MirroredObjectDefinitionsOverride.noOverride());
		DocumentPatch[] patches2 = {};
		anotherMirroredDocument =
				MirroredObjectDefinition.create(TestSpaceOtherObject.class).keepPersistent(true).documentPatches(patches2).buildMirroredDocument(MirroredObjectDefinitionsOverride.noOverride());
		DocumentPatch[] patches1 = {};
		mirroredReloadableDocument = MirroredObjectDefinition.create(TestReloadableSpaceObject.class).documentPatches(patches1).buildMirroredDocument(MirroredObjectDefinitionsOverride.noOverride());
		mirroredObjects = new MirroredObjects(mirroredObject, mirroredReloadableDocument, anotherMirroredDocument);
		documentConverter = TestSpaceObjectFakeConverter.create();
		documentDb = FakeDocumentDb.create();
		mirrorExceptionSpy = new MirrorExceptionSpy();
		mirror = new SpaceMirrorContext(mirroredObjects, documentConverter, documentDb, mirrorExceptionSpy, Plugins.empty());
		mirroredObjectWriter = new MirroredObjectWriter(mirror, new FakeDocumentWriteExceptionHandler());
	}

	@Test
	public void writesAreInsertedInDb() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.WRITE);

		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));


		BasicDBObject expected = documentConverter.convertToDBObject(item1);
		mirroredObject.setDocumentVersion(expected, mirroredObject.getCurrentVersion());

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(1, persisted.size());
		assertEquals(expected, persisted.get(0));
	}

	@Test
	public void writesAllObjectsInDb() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		TestSpaceObject item2 = new TestSpaceObject("2", "hello");
		TestSpaceObject item3 = new TestSpaceObject("3", "hello");
		mirroredObjectWriter.executeBulk(FakeBatchData.create(new FakeBulkItem(item1, DataSyncOperationType.WRITE),
																   new FakeBulkItem(item2, DataSyncOperationType.WRITE),
																   new FakeBulkItem(item3, DataSyncOperationType.WRITE)));

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(3, persisted.size());
	}

	@Test
	public void partialUpdatesAreUpdatedInDb() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		documentDb.getCollection(mirroredObject.getCollectionName()).insert(documentConverter.convertToDBObject(item1));
		item1.setMessage("updated");
		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.PARTIAL_UPDATE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		BasicDBObject expected = documentConverter.convertToDBObject(item1);
		mirroredObject.setDocumentVersion(expected, mirroredObject.getCurrentVersion());

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(1, persisted.size());
		assertEquals(expected, persisted.get(0));
	}

	@Test
	public void udatesAreUpdatedInDb() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		documentDb.getCollection(mirroredObject.getCollectionName()).insert(documentConverter.convertToDBObject(item1));
		item1.setMessage("updated");

		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.UPDATE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		BasicDBObject expected = documentConverter.convertToDBObject(item1);
		mirroredObject.setDocumentVersion(expected, mirroredObject.getCurrentVersion());

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(1, persisted.size());
		assertEquals(expected, persisted.get(0));
	}

	@Test
	public void removesDocumentFromDb() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		BasicDBObject dbObject = documentConverter.convertToDBObject(item1);
		documentDb.getCollection(mirroredObject.getCollectionName()).insert(dbObject);

		TestSpaceObject bulkItemOBject = new TestSpaceObject("1", null);
		FakeBulkItem bulkItem = new FakeBulkItem(bulkItemOBject, DataSyncOperationType.REMOVE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(0, persisted.size());
	}

	@Test
	public void removesDocumentFromDb3() throws Exception {
		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		documentDb.getCollection(mirroredObject.getCollectionName()).insert(documentConverter.convertToDBObject(item1));

		item1.setMessage("hello2");

		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.REMOVE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(0, persisted.size());
	}

	@Test
	public void documentsWithKeepPersistentFlagAreNotRemovedFromDb() throws Exception {
		TestSpaceOtherObject item1 = new TestSpaceOtherObject("1", "hello");
		documentDb.getCollection(mirroredObject.getCollectionName()).insert(documentConverter.convertToDBObject(item1));

		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.REMOVE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));
		BasicDBObject expected = documentConverter.convertToDBObject(item1);

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(1, persisted.size());
		assertEquals(expected, persisted.get(0));
	}

	@Test
	public void ignoresNonMirroredTypes() throws Exception {
		class NonMirroredType {
		}
		FakeBulkItem bulkItem = new FakeBulkItem(new NonMirroredType(), DataSyncOperationType.WRITE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredObject.getCollectionName()).findAll());
		assertEquals(0, persisted.size());
	}

	@Test
	public void notifiesMirrorExceptionListenerOnError() throws Exception {
		documentDb = throwsOnUpdateDocumentDb();
		mirrorExceptionSpy = new MirrorExceptionSpy();
		mirror = new SpaceMirrorContext(mirroredObjects, documentConverter, documentDb, mirrorExceptionSpy, Plugins.empty());
		mirroredObjectWriter = new MirroredObjectWriter(mirror, new FakeDocumentWriteExceptionHandler());

		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.UPDATE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		assertNotNull(mirrorExceptionSpy.lastException);
		assertEquals(RuntimeException.class, mirrorExceptionSpy.lastException.getClass());
	}

	@Test(expected=TransientDocumentWriteException.class)
	public void exceptionFromExceptionHandlerIsPropagated() throws Exception {
		documentDb = throwsOnUpdateDocumentDb();
		mirrorExceptionSpy = new MirrorExceptionSpy();
		mirror = new SpaceMirrorContext(mirroredObjects, documentConverter, documentDb, mirrorExceptionSpy, Plugins.empty());
		mirroredObjectWriter = new MirroredObjectWriter(mirror, new FakeDocumentWriteExceptionHandler(
				new TransientDocumentWriteException(new Exception())));

		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.UPDATE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));
	}

	@Test
	public void exceptionThrownDuringConvertionToMongoDbObjectAreNotPropagated() throws Exception {
		documentConverter = DocumentConverter.create(new DocumentConverter.Provider() {
			@Override
			public BasicDBObject convertToDBObject(Object type) {
				throw new RuntimeException("");
			}

			@Override
			public <T> T convert(Class<T> toType, BasicDBObject document) {
				throw new RuntimeException("");
			}

			@Override
			public Object convert(Object type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Query toQuery(Object template) {
				throw new UnsupportedOperationException();
			}
		});
		mirrorExceptionSpy = new MirrorExceptionSpy();
		mirror = new SpaceMirrorContext(mirroredObjects, documentConverter, documentDb, mirrorExceptionSpy, Plugins.empty());
		mirroredObjectWriter = new MirroredObjectWriter(mirror, new FakeDocumentWriteExceptionHandler());

		TestSpaceObject item1 = new TestSpaceObject("1", "hello");
		FakeBulkItem bulkItem = new FakeBulkItem(item1, DataSyncOperationType.UPDATE);
		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem));

		assertNotNull(mirrorExceptionSpy.lastException);
		assertEquals(RuntimeException.class, mirrorExceptionSpy.lastException.getClass());
	}

	@Test
	public void writesOfReloadedObjectsAreNotInsertedInDb() throws Exception {
		TestReloadableSpaceObject spaceObject = new TestReloadableSpaceObject(1, 1, false, 1, 1);
		TestReloadableSpaceObject spaceObject2 = new TestReloadableSpaceObject(2, 1, false, 2, 1);
		TestReloadableSpaceObject spaceObject3 = new TestReloadableSpaceObject(3, 1, false, 3, 2);
		FakeBulkItem bulkItem = new FakeBulkItem(spaceObject, DataSyncOperationType.WRITE);
		FakeBulkItem bulkItem2 = new FakeBulkItem(spaceObject2, DataSyncOperationType.WRITE);
		FakeBulkItem bulkItem3 = new FakeBulkItem(spaceObject3, DataSyncOperationType.WRITE);

		mirroredObjectWriter.executeBulk(FakeBatchData.create(bulkItem, bulkItem2, bulkItem3));


		BasicDBObject expected = documentConverter.convertToDBObject(spaceObject);
		mirroredReloadableDocument.setDocumentVersion(expected, mirroredReloadableDocument.getCurrentVersion());

		List<DBObject> persisted = Iterables.newArrayList(documentDb.getCollection(mirroredReloadableDocument.getCollectionName()).findAll());
		assertEquals(2, persisted.size());
		assertEquals(2, persisted.get(0).get("_id"));
		assertEquals(3, persisted.get(1).get("_id"));
	}

	private DocumentDb throwsOnUpdateDocumentDb() {
		return DocumentDb.create(name -> new FakeDocumentCollection() {
			@Override
			public void update(DBObject dbObject) {
				throw new RuntimeException();
			}
		});
	}

	static class MirrorExceptionSpy implements MirrorExceptionListener {

		private Exception lastException;

		@Override
		public void onMirrorException(Exception e, MirrorOperation operation, Object[] failedObjects) {
			this.lastException = e;
		}

	}

	private static class FakeBatchData implements OperationsBatchData {

		private final DataSyncOperation[] batchDataItems;

		private FakeBatchData(FakeBulkItem[] items) {
			batchDataItems = items;
		}

		public static FakeBatchData create(FakeBulkItem... items) {
			return new FakeBatchData(items);
		}

		@Override
		public DataSyncOperation[] getBatchDataItems() {
			return this.batchDataItems;
		}

		@Override
		public SynchronizationSourceDetails getSourceDetails() {
			throw new UnsupportedOperationException("Not implemented by FakeBatchData (yet)");
		}

	}

	public static class FakeBulkItem implements DataSyncOperation {

		private final Object item;
		private final DataSyncOperationType operation;

		public FakeBulkItem(Object item, DataSyncOperationType operation) {
			this.item = item;
			this.operation = operation;
		}

		@Override
		public Object getDataAsObject() {
			return this.item;
		}

		@Override
		public DataSyncOperationType getDataSyncOperationType() {
			return operation;
		}

		@Override
		public Object getSpaceId() {
			return null;
		}

		@Override
		public SpaceTypeDescriptor getTypeDescriptor() {
			return null;
		}

		@Override
		public String getUid() {
			return null;
		}

		@Override
		public boolean supportsDataAsDocument() {
			return false;
		}

		@Override
		public boolean supportsDataAsObject() {
			return false;
		}

		@Override
		public boolean supportsGetSpaceId() {
			return false;
		}

		@Override
		public boolean supportsGetTypeDescriptor() {
			return false;
		}

		@Override
		public SpaceDocument getDataAsDocument() {
			return null;
		}
	}

}
