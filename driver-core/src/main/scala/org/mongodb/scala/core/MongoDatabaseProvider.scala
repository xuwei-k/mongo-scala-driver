/**
 * Copyright (c) 2014 MongoDB, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * For questions and comments about this product, please see the project page at:
 *
 * https://github.com/mongodb/mongo-scala-driver
 */
package org.mongodb.scala.core

import _root_.scala.language.higherKinds

import org.mongodb._
import org.mongodb.codecs.{CollectibleDocumentCodec, ObjectIdGenerator}
import org.mongodb.operation.{CommandWriteOperation, CommandReadOperation}

import org.mongodb.scala.core.admin.MongoDatabaseAdminProvider

trait MongoDatabaseProvider {

  this: RequiredTypesProvider =>

  val name: String
  val client: MongoClientProvider
  val options: MongoDatabaseOptions
  val admin: MongoDatabaseAdminProvider

  def apply(collectionName: String): Collection[Document] = collection(collectionName)

  def apply(collectionName: String, collectionOptions: MongoCollectionOptions): Collection[Document] =
    collection(collectionName, collectionOptions)

  def collection(collectionName: String): Collection[Document] =
    collection(collectionName, MongoCollectionOptions(options))

  def collection(collectionName: String, collectionOptions: MongoCollectionOptions): Collection[Document] = {
    val codec = new CollectibleDocumentCodec(collectionOptions.primitiveCodecs, new ObjectIdGenerator())
    collection(collectionName, codec, collectionOptions)
  }

  def collection[T](collectionName: String, codec: CollectibleCodec[T]): Collection[T] =
    collection(collectionName, codec, MongoCollectionOptions(options))

  def collection[T](collectionName: String, codec: CollectibleCodec[T],
                    collectionOptions: MongoCollectionOptions): Collection[T]

  def documentCodec: Codec[Document] = options.documentCodec
  def readPreference: ReadPreference = options.readPreference

  def executeAsyncWriteCommand(command: Document) = client.executeAsync(createWriteOperation(command))
  def executeAsyncReadCommand(command: Document, readPreference: ReadPreference) =
    client.executeAsync(createReadOperation(command), readPreference)

  private def createWriteOperation(command: Document) =
    new CommandWriteOperation(name, command, documentCodec, documentCodec)

  private def createReadOperation(command: Document) =
    new CommandReadOperation(name, command, documentCodec, documentCodec)

}