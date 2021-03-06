/*******************************************************************************
* Copyright (c) 2015 IBM Corp.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.cloudant.spark.riak

import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.catalyst.expressions.Row
import org.apache.spark.sql.types._
import org.apache.spark.sql.sources._
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.StringOps
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import com.cloudant.spark.common._

/**
 * @author yanglei
 */
case class RiakPartitionedPrunedFilteredScan (dbName: String, filter:String=null)
                      (@transient val sqlContext: SQLContext) 
  extends BaseRelation with PrunedFilteredScan 
{
  lazy val config = {
    JsonStoreConfigManager.getConfig(sqlContext, dbName, filter)
  }
  
  @transient lazy val dataAccess = {
      new JsonStoreDataAccess(config)
  }
  
  val schema: StructType = {
      val aRDD = sqlContext.sparkContext.parallelize(dataAccess.getOne())
      sqlContext.read.json(aRDD).schema
  }

  def buildScan(requiredColumns: Array[String], 
                filters: Array[Filter]): RDD[Row] = {
      val filterInterpreter = new FilterInterpreter(filters)
      var searchField:String =  filterInterpreter.firstField
      val (min, minInclusive, max, maxInclusive) = filterInterpreter.getInfo(searchField)
      implicit val columns = requiredColumns
      val (url: String, pusheddown: Boolean) =  config.getRangeUrl(searchField, min,minInclusive, max,maxInclusive, false)
      if (!pusheddown) searchField = null
      implicit val attrToFilters = filterInterpreter.getFiltersForPostProcess(searchField)
      val rows = dataAccess.getAll(url)
      val aRDD = sqlContext.sparkContext.parallelize(rows)
      sqlContext.read.json(aRDD).rdd
  }

}
  
class RiakPartitionedPrunedFilteredRP extends RelationProvider {

  def createRelation(sqlContext: SQLContext, parameters: Map[String, String]) = {
    RiakPartitionedPrunedFilteredScan(
      parameters("path"))(sqlContext)
  }
  
}