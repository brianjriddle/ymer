package se.avanzabank.mongodb.support.mirror;

import static org.junit.Assert.*;

import com.gigaspaces.annotation.pojo.SpaceRouting;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.Test;
import org.openspaces.core.cluster.ClusterInfo;
import org.springframework.data.mongodb.core.query.Query;


public class VersionedMongoDBExternalDataSourceTest {

	private final Integer partitionId = 1;
	private final int numberOfInstances = 2;

	@Test
	public void documentsMustNotBeWrittenToDbBeforeAllElementsAreLoaded() throws Exception {
		MirroredDocument<FakeSpaceObject> patchedMirroredDocument = new MirroredDocument<>(FakeSpaceObject.class, new FakeSpaceObjectV1Patch());
		DocumentDb fakeDb = FakeDocumentDb.create();
		SpaceMirrorContext spaceMirror = new SpaceMirrorContext(new MirroredDocuments(patchedMirroredDocument), FakeDocumentConverter.create(), fakeDb);
		VersionedMongoDBExternalDataSource externalDataSourceForPartition1 = new VersionedMongoDBExternalDataSource(spaceMirror);
		externalDataSourceForPartition1.setClusterInfo(new ClusterInfo("", partitionId, null, numberOfInstances, 0));

		DocumentCollection documentCollection = fakeDb.getCollection(patchedMirroredDocument.getCollectionName());
		BasicDBObject doc1 = new BasicDBObject();
		doc1.put("_id", 1);
		doc1.put("spaceRouting", 1);

		BasicDBObject doc2 = new BasicDBObject();
		doc2.put("_id", 2);
		doc2.put("spaceRouting", 2);

		BasicDBObject doc3 = new BasicDBObject();
		doc3.put("_id", 3);
		doc3.put("spaceRouting", 3);

		documentCollection.insert(doc1);
		documentCollection.insert(doc2);
		documentCollection.insert(doc3);

		Iterable<FakeSpaceObject> loadInitialLoadData = externalDataSourceForPartition1.loadInitialLoadData(patchedMirroredDocument);
		assertEquals(1, Iterables.sizeOf(loadInitialLoadData));
	}

	@Test
	public void loadsAndPatchesASingleDocumentById() throws Exception {
		MirroredDocument<TestReloadableSpaceObject> mirroredDocument = new MirroredDocument<>(TestReloadableSpaceObject.class, new FakeSpaceObjectV1Patch());
		DocumentDb documentDb = FakeDocumentDb.create();
		SpaceMirrorContext spaceMirror = new SpaceMirrorContext(
				new MirroredDocuments(mirroredDocument),
				TestSpaceObjectFakeConverter.create(),
				documentDb);
		VersionedMongoDBExternalDataSource externalDataSourceForPartition1 = new VersionedMongoDBExternalDataSource(spaceMirror);
		externalDataSourceForPartition1.setClusterInfo(new ClusterInfo("", partitionId, null, numberOfInstances, 0));

		DocumentCollection documentCollection = documentDb.getCollection(mirroredDocument.getCollectionName());
		BasicDBObject doc1 = new BasicDBObject();
		doc1.put("_id", 1);
		doc1.put("spaceRouting", 1);
		doc1.put("versionID", 1);

		BasicDBObject doc2 = new BasicDBObject();
		doc2.put("_id", 2);
		doc2.put("spaceRouting", 2);
		doc2.put("versionID", 1);

		BasicDBObject doc3 = new BasicDBObject();
		doc3.put("_id", 3);
		doc3.put("spaceRouting", 3);
		doc3.put("versionID", 1);

		documentCollection.insert(doc1);
		documentCollection.insert(doc2);
		documentCollection.insert(doc3);
		assertNotNull(externalDataSourceForPartition1.reloadObject(TestReloadableSpaceObject.class, 2));

		DBObject dbObject = documentDb.getCollection(mirroredDocument.getCollectionName()).findById(2);
		assertFalse(mirroredDocument.requiresPatching(new BasicDBObject(dbObject.toMap())));
	}


	private static class FakeSpaceObject {

		private int id;
		private int spaceRouting;

		public FakeSpaceObject(int id, int spaceRouting) {
			this.id = id;
			this.spaceRouting = spaceRouting;
		}

		public FakeSpaceObject() {
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		@SpaceRouting
		public int getSpaceRouting() {
			return spaceRouting;
		}

		public void setSpaceRouting(int routingKey) {
			this.spaceRouting = routingKey;
		}

		@Override
		public int hashCode() {
			return toString().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return toString().equals(obj.toString());
		}

		@Override
		public String toString() {
			return "FakeSpaceObject [id=" + id + ", spaceRouting=" + spaceRouting + "]";
		}

	}

	private static class FakeSpaceObjectV1Patch implements DocumentPatch {

		@Override
		public void apply(BasicDBObject dbObject) {
			dbObject.put("patched", true);
		}

		@Override
		public int patchedVersion() {
			return 1;
		}

	}

	private static class FakeDocumentConverter implements DocumentConverter.Provider {

		@Override
		public <T> T convert(Class<T> toType, BasicDBObject document) {
			FakeSpaceObject spaceObject = new FakeSpaceObject();
			spaceObject.setSpaceRouting(document.getInt("spaceRouting"));
			spaceObject.setId(document.getInt("_id"));
			return (T) spaceObject;
		}

		@Override
		public BasicDBObject convertToDBObject(Object type) {
			throw new UnsupportedOperationException();
		}

		public static DocumentConverter create() {
			return DocumentConverter.create(new FakeDocumentConverter());
		}

		@Override
		public Object convert(Object type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Query toQuery(Object template) {
			throw new UnsupportedOperationException();
		}
	}

}
