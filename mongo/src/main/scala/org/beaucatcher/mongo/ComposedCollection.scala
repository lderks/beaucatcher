package org.beaucatcher.mongo

/**
 * A Collection that backends to another Collection. The two may have different query, entity, and ID types.
 * This is an internal implementation class not exported from the library.
 */
private[beaucatcher] abstract trait ComposedSyncCollection[OuterQueryType, OuterEntityType, OuterIdType, OuterValueType, InnerQueryType, InnerEntityType, InnerIdType, InnerValueType]
    extends SyncCollection[OuterQueryType, OuterEntityType, OuterIdType, OuterValueType] {

    protected val inner : SyncCollection[InnerQueryType, InnerEntityType, InnerIdType, InnerValueType]

    protected val queryComposer : QueryComposer[OuterQueryType, InnerQueryType]
    protected val entityComposer : EntityComposer[OuterEntityType, InnerEntityType]
    protected val idComposer : IdComposer[OuterIdType, InnerIdType]
    protected val valueComposer : ValueComposer[OuterValueType, InnerValueType]
    protected val exceptionMapper : PartialFunction[Throwable, Throwable] = {
        case _ if false => throw new Exception("undefined exceptionMapper was applied")
    }

    private def withExceptionsMapped[A](body : => A) = {
        try {
            body
        } catch {
            case e if exceptionMapper.isDefinedAt(e) =>
                throw exceptionMapper.apply(e)
        }
    }

    override def name : String = withExceptionsMapped {
        inner.name
    }

    override def emptyQuery : OuterQueryType = withExceptionsMapped {
        queryOut(inner.emptyQuery)
    }

    override def count(query : OuterQueryType, options : CountOptions) : Long = withExceptionsMapped {
        inner.count(queryIn(query), options)
    }

    override def distinct(key : String, options : DistinctOptions[OuterQueryType]) : Seq[OuterValueType] = withExceptionsMapped {
        inner.distinct(key, options.convert(queryIn(_))) map { valueOut(_) }
    }

    override def find(query : OuterQueryType, options : FindOptions) : Iterator[OuterEntityType] = withExceptionsMapped {
        inner.find(queryIn(query), options).map(entityOut(_))
    }

    override def findOne(query : OuterQueryType, options : FindOneOptions) : Option[OuterEntityType] = withExceptionsMapped {
        entityOut(inner.findOne(queryIn(query), options))
    }

    override def findOneById(id : OuterIdType, options : FindOneByIdOptions) : Option[OuterEntityType] = withExceptionsMapped {
        inner.findOneById(idIn(id), options) flatMap { e => Some(entityOut(e)) }
    }

    override def findAndModify(query : OuterQueryType, update : Option[OuterQueryType], options : FindAndModifyOptions[OuterQueryType]) : Option[OuterEntityType] = withExceptionsMapped {
        entityOut(inner.findAndModify(queryIn(query), queryIn(update), options.convert(queryIn(_))))
    }

    override def insert(o : OuterEntityType) : WriteResult = withExceptionsMapped {
        inner.insert(entityIn(o))
    }

    override def update(query : OuterQueryType, modifier : OuterQueryType, options : UpdateOptions) : WriteResult = withExceptionsMapped {
        inner.update(queryIn(query), queryIn(modifier), options)
    }

    override def remove(query : OuterQueryType) : WriteResult = withExceptionsMapped {
        inner.remove(queryIn(query))
    }

    override def removeById(id : OuterIdType) : WriteResult = withExceptionsMapped {
        inner.removeById(idIn(id))
    }

    def ensureIndex(keys : OuterQueryType, options : IndexOptions) : WriteResult = withExceptionsMapped {
        inner.ensureIndex(queryIn(keys), options)
    }

    def dropIndex(name : String) : CommandResult = withExceptionsMapped {
        inner.dropIndex(name)
    }

    /* These are all final because you should override the composers instead, these are
     * just here to save typing
     */
    final protected def queryIn(q : OuterQueryType) : InnerQueryType = queryComposer.queryIn(q)
    final protected def queryIn(q : Option[OuterQueryType]) : Option[InnerQueryType] =
        q map { queryIn(_) }
    final protected def queryOut(q : InnerQueryType) : OuterQueryType = queryComposer.queryOut(q)
    final protected def queryOut(q : Option[InnerQueryType]) : Option[OuterQueryType] =
        q map { queryOut(_) }
    final protected def entityIn(o : OuterEntityType) : InnerEntityType = entityComposer.entityIn(o)
    final protected def entityIn(o : Option[OuterEntityType]) : Option[InnerEntityType] =
        o map { entityIn(_) }
    final protected def entityOut(o : InnerEntityType) : OuterEntityType = entityComposer.entityOut(o)
    final protected def entityOut(o : Option[InnerEntityType]) : Option[OuterEntityType] =
        o map { entityOut(_) }
    final protected def idIn(id : OuterIdType) : InnerIdType = idComposer.idIn(id)
    final protected def idIn(id : Option[OuterIdType]) : Option[InnerIdType] =
        id map { idIn(_) }
    final protected def idOut(id : InnerIdType) : OuterIdType = idComposer.idOut(id)
    final protected def idOut(id : Option[InnerIdType]) : Option[OuterIdType] =
        id map { idOut(_) }
    final protected def valueIn(v : OuterValueType) : InnerValueType = valueComposer.valueIn(v)
    final protected def valueIn(v : Option[OuterValueType]) : Option[InnerValueType] =
        v map { valueIn(_) }
    final protected def valueOut(v : InnerValueType) : OuterValueType = valueComposer.valueOut(v)
    final protected def valueOut(v : Option[InnerValueType]) : Option[OuterValueType] =
        v map { valueOut(_) }
}