package org.javers.core.cases


import org.javers.core.JaversBuilder
import org.javers.core.diff.Diff
import org.javers.core.diff.changetype.ReferenceChange
import org.javers.core.metamodel.annotation.Id
import spock.lang.Specification

/**
 * https://stackoverflow.com/questions/56576291/javers-returns-incomplete-diff-when-entity-field-type-is-changed-inheritance
 *
 * @author Dmitry Grankin
 */
class DiffForPolymorphicField extends Specification {

    def "should show full diff when a type of field is changed"() {
        given:
        def javers = JaversBuilder
                .javers()
                .registerEntities(Entity, AnotherEntity)
                .registerValueObjects(ConcreteA, ConcreteB)
                .build()
        AnotherEntity anotherEntity = new AnotherEntity("uuid", "name")

        int comparedEntityId = 123
        AbstractType originalValueObject = new ConcreteA(anotherEntity)
        Entity originalEntity = new Entity(comparedEntityId, originalValueObject)

        AbstractType updatedValueObject = new ConcreteB(anotherEntity)
        Entity updatedEntity = new Entity(comparedEntityId, updatedValueObject)

        when:
        Diff diff = javers.compare(originalEntity, updatedEntity)

        then:
        def referenceChanges = diff.getChangesByType(ReferenceChange)
        def removalOf_ConcreteA_entityA = referenceChanges
                .stream()
                .filter({ change -> return change.getPropertyName() == 'entityA' })
                .findAny()
        def additionOf_ConcreteB_entityB = referenceChanges
                .stream()
                .filter({ change -> return change.getPropertyName() == 'entityB' })
                .findAny()
        referenceChanges.size() == 2
        removalOf_ConcreteA_entityA.isPresent()
        additionOf_ConcreteB_entityB.isPresent()
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

abstract class AbstractType {}

// A value object.
class ConcreteA extends AbstractType {

    private final AnotherEntity entityA

    ConcreteA(AnotherEntity entityA) {
        this.entityA = entityA
    }
}

// A value object.
class ConcreteB extends AbstractType {

    private final AnotherEntity entityB

    // Other fields are omitted for simplicity.

    ConcreteB(AnotherEntity entityB) {
        this.entityB = entityB
    }
}

// The class registered as an entity.
class AnotherEntity {

    @Id
    private final String uuid
    private final String name

    AnotherEntity(String uuid, String name) {
        this.uuid = uuid
        this.name = name
    }
}
