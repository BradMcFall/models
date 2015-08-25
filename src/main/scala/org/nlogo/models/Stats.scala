package org.nlogo.models

import java.awt.Font
import java.io.File
import java.util.Date

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.util.Try

import org.jfree.chart.labels.ItemLabelAnchor
import org.jfree.chart.labels.ItemLabelPosition
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.renderer.category.BarRenderer
import org.jfree.chart.title.TextTitle
import org.jfree.graphics2d.svg.SVGGraphics2D
import org.jfree.graphics2d.svg.SVGUtils
import org.jfree.ui.TextAnchor
import org.nlogo.nvm.Instruction

import scalax.chart.Chart
import scalax.chart.api.BarChart

object Stats {

  def exportPrimitivesUsagePlot(): Unit = {

    def reporterNames(instruction: Instruction): Iterable[String] =
      for {
        ins <- instruction.args
        name <- instructionName(ins).toSeq ++ reporterNames(ins)
      } yield name

    def instructionName(instruction: Instruction): Option[String] =
      for {
        token <- Option(instruction.token)
        if token.name.nonEmpty
        if token.name.toLowerCase == token.name // exclude user-defined
        if !(token.name.charAt(0) == '"') // exclude strings
        if Try(token.name.toDouble).isFailure // exclude numbers
      } yield token.name

    def tokenNames(model: Model) = withWorkspace(model) { ws =>
      for {
        (_, procedure) <- ws.getProcedures.asScala
        command <- procedure.code
        commandName = instructionName(command)
        name <- commandName.toSeq ++ reporterNames(command)
      } yield name
    }

    val data = Model.libraryModels
      .filterNot(model => model.is3d || model.code.lines.exists(_.startsWith("extensions")))
      .flatMap(model => tokenNames(model).toSeq.distinct.map(_ -> model))
      .groupBy(_._1).mapValues(_.size)
      .toSeq
      .sortBy(t => (0 - t._2, t._1))

    val chart = BarChart(data,
      title = "Usage of primitives in the Models Library",
      legend = false
    )
    chart.peer.addSubtitle(new TextTitle(
      "(excluding 3D models and models using extensions)\n" +
        new Date().toString())
    )

    val renderer = new BarRenderer
    renderer.setShadowVisible(false)
    renderer.setDrawBarOutline(true)
    renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator)
    renderer.setBaseItemLabelsVisible(true)
    renderer.setBasePositiveItemLabelPosition(
      new ItemLabelPosition(ItemLabelAnchor.OUTSIDE3, TextAnchor.CENTER_LEFT)
    )

    val plot = chart.peer.getCategoryPlot
    plot.setRenderer(renderer)
    plot.setOrientation(PlotOrientation.HORIZONTAL)
    plot.getRangeAxis.setLabel("Number of models")
    plot.getDomainAxis.setTickLabelFont(new Font("Monospaced", Font.PLAIN, 12))
    plot.getDomainAxis.setUpperMargin(0.005)
    plot.getDomainAxis.setLowerMargin(0.005)
    saveAsSVG(chart, "test/stats/usage_of_primitives.svg", (800, 4500))
  }

  // shouldn't be needed anymore once
  // https://github.com/wookietreiber/scala-chart/issues/12
  // makes it into a scala-chart release
  def saveAsSVG(chart: Chart, file: String, resolution: (Int, Int)): Unit = {
    val (width, height) = resolution
    val g2 = new SVGGraphics2D(width, height)
    chart.peer.draw(g2, new java.awt.Rectangle(
      new java.awt.Dimension(width, height))
    )
    val svg = g2.getSVGElement
    g2.dispose()
    SVGUtils.writeToSVG(new File(file), svg)
  }

  def main(args: Array[String]): Unit = exportPrimitivesUsagePlot()
}
