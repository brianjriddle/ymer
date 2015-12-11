package se.avanzabank.mongodb.support.mirror;

import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.github.fakemongo.Fongo;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import se.avanzabank.mongodb.support.mirror.VersionedMongoDbExternalDataSourceInitialLoadIntegrationTest.TestSpaceObjectV1Patch;


public class MongoPartitionFilterTest {

	private final MirroredDocument<TestSpaceObject> mirroredDocument = new MirroredDocument<>(TestSpaceObject.class, new TestSpaceObjectV1Patch());

	public final Fongo mongoRule = new Fongo("db");

	@Test
	public void canQueryAllNumbers() throws Exception {
		final int NUM_PARTITIONS = 13;
		final int ID_LOWER_BOUND = -100;
		final int ID_UPPER_BOUND = 100;

		DBCollection collection = mongoRule.getMongo().getDB("_test").getCollection("testCollection");
		for (int i = ID_LOWER_BOUND; i < ID_UPPER_BOUND; i++) {
			collection.insert(BasicDBObjectBuilder.start("_id", i).add("_routingKey", ((Integer)i).hashCode()).get());
		}

		Set<Integer> found = new HashSet<>();
		for (int i = 1; i <= NUM_PARTITIONS; i++) {
			DBCursor cur = collection.find(MongoPartitionFilter.create(SpaceObjectFilter.partitionFilter(mirroredDocument, i, NUM_PARTITIONS)).toDBObject());
			found.addAll(extractIds(cur.toArray()));
		}

		for (int i = ID_LOWER_BOUND; i < ID_UPPER_BOUND; i++) {
			assertTrue(i + " not found!", found.contains(i));
		}
	}

	private Collection<? extends Integer> extractIds(List<DBObject> array) {
		Collection<Integer> result = new HashSet<>();
		for (DBObject e : array) {
			result.add((Integer)e.get("_id"));
		}
		return result;
	}

}
