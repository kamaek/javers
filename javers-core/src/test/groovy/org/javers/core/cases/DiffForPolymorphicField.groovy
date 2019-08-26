package org.javers.core.cases

import org.javers.core.JaversBuilder
import org.javers.core.diff.Diff
import org.javers.core.metamodel.annotation.Id
import spock.lang.Specification

/**
 * @author Dmitry Grankin
 */
class DiffForPolymorphicField extends Specification {

    def "should show diff when only a type of field is changed"() {
        given:
        def javers = JaversBuilder
                .javers()
                .registerEntities(Entity)
                .registerValueObjects(ConcreteA, ConcreteB)
                .build()

        int comparedEntityId = 123
        AbstractType originalValueObject = new ConcreteA(5L)
        Entity originalEntity = new Entity(comparedEntityId, originalValueObject)

        AbstractType updatedValueObject = new ConcreteB(5L)
        Entity updatedEntity = new Entity(comparedEntityId, updatedValueObject)

        when:
        Diff diff = javers.compare(originalEntity, updatedEntity)

        then:
        def changes = diff.getChanges()
        changes.size() == 1
    }
}

// An entity.
class Entity {

    @Id
    private final int id
    private final AbstractType field

    Entity(int id, AbstractType field) {
        this.id = id
        this.field = field
    }
}

abstract class AbstractType {

    private final long tagNumber;

    AbstractType(long tagNumber) {
        this.tagNumber = tagNumber
    }
}

// A value object.
class ConcreteA extends AbstractType {

    ConcreteA(long tagNumber) {
        super(tagNumber)
    }
}

// A value object.
class ConcreteB extends AbstractType {

    ConcreteB(long tagNumber) {
        super(tagNumber)
    }
}
