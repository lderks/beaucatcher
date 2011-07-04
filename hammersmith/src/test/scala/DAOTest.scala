import org.beaucatcher.bson.Implicits._
import org.beaucatcher.bson._
import org.beaucatcher.hammersmith._
import org.beaucatcher.mongo._
import org.bson.types._
import org.junit.Assert._
import org.junit._

package foohammersmith {
    case class Foo(_id : ObjectId, intField : Int, stringField : String) extends abstractfoo.AbstractFoo

    object Foo extends CollectionOperations[Foo, ObjectId]
        with HammersmithTestProvider {
        def customQuery[E](implicit chooser : SyncDAOChooser[E, _]) = {
            syncDAO[E].find(BObject("intField" -> 23))
        }

        System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "debug")
    }

    case class FooWithIntId(_id : Int, intField : Int, stringField : String) extends abstractfoo.AbstractFooWithIntId

    object FooWithIntId extends CollectionOperations[FooWithIntId, Int]
        with HammersmithTestProvider {
        def customQuery[E](implicit chooser : SyncDAOChooser[E, _]) = {
            syncDAO[E].find(BObject("intField" -> 23))
        }
    }

    case class FooWithOptionalField(_id : ObjectId, intField : Int, stringField : Option[String]) extends abstractfoo.AbstractFooWithOptionalField

    object FooWithOptionalField extends CollectionOperations[FooWithOptionalField, ObjectId]
        with HammersmithTestProvider {
    }
}

import foohammersmith._
class DAOTestHammersmith
    extends AbstractDAOTest[Foo, FooWithIntId, FooWithOptionalField](Foo, FooWithIntId, FooWithOptionalField) {
    override def newFoo(_id : ObjectId, intField : Int, stringField : String) = Foo(_id, intField, stringField)
    override def newFooWithIntId(_id : Int, intField : Int, stringField : String) = FooWithIntId(_id, intField, stringField)
    override def newFooWithOptionalField(_id : ObjectId, intField : Int, stringField : Option[String]) = FooWithOptionalField(_id, intField, stringField)

    // factoring this up into AbstractDAOTest is just too annoying
    @Test
    def testCustomQueryReturnsVariousEntityTypes() {
        val foo = Foo(new ObjectId(), 23, "woohoo")
        Foo.syncDAO[Foo].save(foo)

        val objects = Foo.customQuery[BObject].toIndexedSeq
        assertEquals(1, objects.size)
        assertEquals(BInt32(23), objects(0).get("intField").get)
        assertEquals(BString("woohoo"), objects(0).get("stringField").get)

        val caseClasses = Foo.customQuery[Foo].toIndexedSeq
        assertEquals(1, caseClasses.size)
        val f = caseClasses(0)
        assertEquals(23, f.intField)
        assertEquals("woohoo", f.stringField)
    }

    // factoring this up into AbstractDAOTest is just too annoying
    @Test
    def testCustomQueryReturnsVariousEntityTypesWithIntId() {
        val foo = FooWithIntId(100, 23, "woohoo")
        FooWithIntId.syncDAO[FooWithIntId].save(foo)

        val objects = FooWithIntId.customQuery[BObject].toIndexedSeq
        assertEquals(1, objects.size)
        assertEquals(BInt32(23), objects(0).get("intField").get)
        assertEquals(BString("woohoo"), objects(0).get("stringField").get)

        val caseClasses = FooWithIntId.customQuery[FooWithIntId].toIndexedSeq
        assertEquals(1, caseClasses.size)
        val f = caseClasses(0)
        assertEquals(23, f.intField)
        assertEquals("woohoo", f.stringField)
    }
}