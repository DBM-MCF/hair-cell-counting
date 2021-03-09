/*
Count hair cells

Description:
Semi-automated script to count cells along and around a drawn line.
Uses custom trained StarDist model.
Will max project nd2 images, allow you to draw multiple lines along the nuclei.
Cells will be counted in a rectangle around each line.
Manually correction of the count is possible.

Output:
- csv file with the counts for each line
- overview jpg of the counts for each line region crop
- ROI zip file for each line region crop

Tested with Fiji version: 2.0.0-rc-69/1.52p

The current StarDist2D model StarDist2D_model_Maurizio_20200623 requires:
TensorFlow 1.14.0 (Fiji settings)



Requirements (update sites):
- StarDist
- CSBDeep
- Biomedgroup

@author: Loïc Sauteur - DBM Basel - loic.sauteur@unibas.ch

 */




// imports
import groovy.io.FileType
import ij.IJ
import ij.ImagePlus
import ij.Menus
import ij.gui.NonBlockingGenericDialog
import ij.gui.Overlay
import ij.gui.PointRoi
import ij.gui.PolygonRoi
import ij.gui.Roi
import ij.gui.WaitForUserDialog
import ij.measure.ResultsTable
import ij.plugin.Duplicator
import ij.plugin.ZProjector
import ij.plugin.frame.RoiManager
import loci.plugins.BF
import loci.plugins.in.ImporterOptions

import de.csbdresden.stardist.StarDist2D

#@ CommandService command
#@ ConvertService convertService

// script parameters
#@File(label="Select the folder with your .nd2 images", style="directory", description="Also the results of the script will be stored here") dir
#@File(label="Specify the location of your StarDist model", style="file") SDmodel
#@Float(label="Probability threshold", value=0.58) probT
#@Float(label="NMS threshold", value=0.3) nmsT
#@Boolean(label="Cochlear recognition (experimental)", value=true) cochlearRecog


// variables
//suffix = ".nd2"
suffix = ".nd2"
destination = dir.getAbsolutePath() + File.separator
result = "sep=;\nImage;# Line;Category;Inner hair cell count;Outer hair cell count;Total cell count\n"
// used to define the different segments of the snail
percentages = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]




// Script start
list = []
count = 0
def dirFile = new File(destination)
dirFile.eachFileRecurse(FileType.FILES) { file ->
    if (file.name.endsWith(suffix)) {
        count++
        list << file
    }
}
list.sort()

IJ.run("Close All")
main()

println("Script end")
// Script end


def main() {
    // check if plugins are installed
    if (!checkForPlugin("StarDist 2D", "Please install the StarDist (and CSBDeep) plugin",
            "Please enable the StarDist and CSBDeep plugin sites.")) return
    if (!checkForPlugin("Run your IsoNet", "Please install the CSBDeep plugin",
            "Please enable the CSBDeep plugin site.")) return
    if (!checkForPlugin("Ridge Detection", "Please install the Ridge Detection plugin",
            "Please enable the Biomedgroup update site")) return
    // make sure the StarDist model is a zip file
    if (!SDmodel.getAbsolutePath().endsWith(".zip")) {
        IJ.error("Invalid StarDist model file type",
                "${SDmodel.getAbsoluteFile()}\ndoes not appear to be the correct file type.\n" +
                        "Make sure to choose a StarDist model .zip file.")
    }

    // loop over image files
    curImage = 1
    for (image in list) {
        // open the image
        println("Opening image $curImage out of $count images.")
        if (suffix == ".ome.tif") {
            imp = IJ.openImage(image.getAbsolutePath())
            imp.hide()
        }
        else {
            imp = openImageWithBF(image.getAbsolutePath())
        }
        oriTitle = imp.title
        xyRes = imp.getCalibration().getX(1.0)
        if (imp.getNSlices()>1) {
            println("\tProjecting stack...")
            projection = new ZProjector().run(imp,"max")
            imp.close()
            imp = projection
            IJ.run("Collect Garbage", "")
        }

        // currently experimental cochlear recognition (20200727)
        if (cochlearRecog) {
            // Identify the snail to visualize the different segments
            println("\tIdentifying snail...")
            labelImp = imp.duplicate()
            labelImp = applyStarDistChoice(labelImp, SDmodel.getAbsolutePath(), probT, nmsT, "Label Image")
            //labelImp.show()
            labelImp.setTitle("labelImage_ori")
            println("\tConnecting snail segments...")
            snailLineROI = findSnail(labelImp) // identify the snail line and add to RoiManager
            if (snailLineROI == null) {
                IJ.error("No cochlear could be identified for this image. Sorry!")
            }
            else {
                calcSnailLine(snailLineROI) // fills the RoiManager with points
                rm = RoiManager.getInstance()
                snailOL = new Overlay()
                println("\tAdding points to overlay...")
                rm.moveRoisToOverlay(imp)
                imp.setHideOverlay(false)
            }
        }

        // allow daring lines
        rm = RoiManager.getInstance()
        if (rm == null) rm = new RoiManager()
        else rm.reset()
        imp.show()
        if (cochlearRecog && snailLineROI != null) {
            IJ.run("Labels...", "color=white font=12 show use draw") // show overlay labels
        }
        IJ.setTool("line")
        wfud = new WaitForUserDialog("Draw lines",
                "Draw lines along 20 inner hair cells,\n" +
                "Add each line to the ROI manager (press 't').\n" +
                "Click OK to continue.")
        wfud.setSize(400, 200)
        wfud.show()
        rm = RoiManager.getInstance()
        drawnLines = rm.getRoisAsArray()
        imp.hide()


        // process each line
        lineCount = 1
        for (line in drawnLines) {
            //  ---------   Draw a rectangle around the line    ---------------------   //
            // compute a vector in line direction
            // (dx, dy) = vector with length 90; (-dy, dx) is perpendicular to the line
            dx = line.x2 - line.x1
            dy = line.y2 - line.y1
            d = Math.sqrt(dx*dx + dy*dy)
            dx = 150 * dx / d
            dy = 150 * dy / d
            int[] xPoints = [line.x1 - dy, line.x1 + dy, line.x2 + dy, line.x2 - dy] as int[]
            int[] yPoints = [line.y1 + dx, line.y1 - dx, line.y2 - dx, line.y2 + dx] as int[]
            imp.setRoi(new PolygonRoi(xPoints, yPoints, 4, Roi.POLYGON))

            // ---------    Proceed to analyse only the crop   ------------    //
            // crop around the rectangle and add the rectangle to the overlay of the crop
            impCrop = new Duplicator().run(imp)
            impCrop.show()
            // recreate the rectangle
            xmin = xPoints[0]
            ymin = yPoints[0]
            for (int i = 1; i < 4; i++) {
                if (xmin > xPoints[i]) xmin = xPoints[i]
                if (ymin > yPoints[i]) ymin = yPoints[i]
            }
            for (int i = 0; i < 4; i++) {
                xPoints[i] -= xmin
                yPoints[i] -= ymin
            }
            // TODO add also the line?

            cropSelection = new PolygonRoi(xPoints, yPoints, 4, Roi.POLYGON)
            ov = new Overlay()
            ov.add(cropSelection)
            impCrop.setOverlay(ov)
            impCrop.setHideOverlay(false)
            IJ.run(impCrop, "Grays", "")

            //  --------    Identify nuclei with StarDist   ------------------- //
            rm = applyStarDist(impCrop, SDmodel.getAbsolutePath(), probT, nmsT)

            // select only the nuclei within the rectangle crop
            roiCount = 0
            // count nuclei within rectangle
            for (i=0; i < rm.count; i++) {
                if (cropSelection.contains(rm.getRoi(i).getStatistics().xCentroid as Integer, rm.getRoi(i).getStatistics().yCentroid as Integer)) roiCount++
            }
            // save ROIs within rectangle in new array
            roiArray = []
            for (int i = 0; i < rm.count; i++) {
                if (cropSelection.contains(rm.getRoi(i).getStatistics().xCentroid as Integer, rm.getRoi(i).getStatistics().yCentroid as Integer)) {
                    roiArray.add(rm.getRoi(i))
                }
            }
            // add the selected ROIs to the ROImanager
            rm.reset()
            for (int i = 0; i < roiCount; i++) {
                rm.add(roiArray[i], i)
                rm.rename(i, (i+1).toString())
            }
            rm.runCommand(impCrop,"Show All")

            //  ----------- Allow user to adjust cell detection ----------------    //
            IJ.setTool("oval")
            (baseCount, category) = checkCellCountDialog("Adjust the cell count") // retunrs (Int BaseCount, Sting Category)

            // create overview image
            rm.runCommand(impCrop, "Show None")
            cropOverview = impCrop.flatten()
            impCrop.hide()
            impCrop.close()
            markOnRGB(cropOverview, rm, [255,0,0], 1)

            // -----------------  save results    ------------------------------  //
            result += [oriTitle, lineCount, category, baseCount, (rm.count - baseCount), rm.count, "\n"].join(";")
            file = new File(destination + "_Results.csv")
            file.write(result)
            println("\tSaved/updated results in: " + destination + "_Results.csv")

            rm.runCommand("Save", destination + oriTitle + "_Line${lineCount}_${category}_ROIs.zip")
            println("\tSaved ROIs for Line $lineCount as: " + destination + oriTitle + "_Line${lineCount}_${category}_ROIs.zip")

            IJ.saveAs(cropOverview, "Jpeg", destination + oriTitle + "_Line${lineCount}_${category}.jpg")
            println("\tSaved overview image as: " + destination + oriTitle + "_Line${lineCount}_${category}.jpg")
            cropOverview.close()

            rm.reset()
            lineCount++
        }
        imp.close()
        IJ.run("Collect Garbage", "")

        println("Finished processing image $curImage out of $count images.\n")
        curImage++
    }

}




//  -------------   Functions   --------------  //


/**
 * identify the snail from the label mask (as a line)
 * some steps need to be check for run errors (FIXME)
 * if no lines detected with Ridge detection, method return null
 * @param labelMask = stardist label mask
 * @return = polyline ROI (or null)
 */
def findSnail(ImagePlus labelMask){
    // labelMask = stardist label mask
    IJ.setRawThreshold(labelMask, 1, 65535, null)
    labelMask.setProcessor(labelMask.getProcessor().createMask())
    labelMask.getProcessor().invertLut()

    IJ.run(labelMask, "Options...", "iterations=25 count=1 do=Dilate")
    IJ.run(labelMask, "Fill Holes", "")
    IJ.run(labelMask, "Options...", "iterations=22 count=1 do=Erode")

    // create a mask of only the snail
    IJ.run(labelMask, "Analyze Particles...", "size=20000-Infinity show=Masks clear")
    labelMask.close() // close the duplicate
    mask = IJ.getImage()
    mask.hide()

    // skeletonize snail
    IJ.run(mask, "Skeletonize", "")
    mask.setTitle("skeleton")
    // this will prune some small appendixes and also the start and end (which is probably ok)
    IJ.run(mask, "Analyze Skeleton (2D/3D)", "prune=none prune_0")
    tempImp = IJ.getImage() // close the skeleton result image
    tempImp.hide()
    tempImp.close()
    rt = ResultsTable.getResultsWindow()
    rt.close(false)

    // identify the line
    rm = RoiManager.getInstance()
    if (rm == null) rm = new RoiManager()
    else rm.reset()
    mask.getProcessor().invertLut()
    IJ.run(mask, "Ridge Detection", "line_width=1 high_contrast=250 low_contrast=100 extend_line add_to_manager method_for_overlap_resolution=NONE sigma=1.5 lower_threshold=10 upper_threshold=28.22 minimum_line_length=1000 maximum=0");
    rm = RoiManager.getInstance()

    // delete junction points in rm
    if (rm.count == 0) {
        mask.close()
        return null
    }
    rmCount = rm.count
    for (int i = rmCount - 1; i >= 0; i--) {
        if (rm.getRoi(i).getName().startsWith("JP")) {
            rm.select(i)
            rm.runCommand(imp, "Delete")
        }
    }

    // re-order lines from apex to outside
    rm = reorderLinesInRM(mask, rm)

    // create a single line from the lines in the rm

    xCoo = []
    yCoo = []
    for (int i = 0; i < rm.count; i++) {
        line = rm.getRoi(i) as PolygonRoi
        lineXcoo = line.getFloatPolygon().xpoints
        lineYcoo = line.getFloatPolygon().ypoints
        for (int j = 0; j < lineXcoo.length; j++) {
            xCoo << lineXcoo[j]
            yCoo << lineYcoo[j]
        }
    }
    rm.reset()
    lineROI = new PolygonRoi(xCoo as float[], yCoo as float[], xCoo.size(), Roi.POLYLINE)
    rm.addRoi(lineROI)
    rm.rename(0, "Cochlear_Line")
    mask.close()
    return lineROI
}

/**
 * Returns points for every 10% or
 * defined by the global array percentages segment of line (including 0% and 100%)
 * @param sline = PolygonROI
 * @return fills the ROI manager with the points
 */
def calcSnailLine(PolygonRoi sline) {
    xCoo = sline.getFloatPolygon().xpoints
    yCoo = sline.getFloatPolygon().ypoints
    length = 0
    for (int i = 0; i < xCoo.length - 1; i++) {
        length += calcPointDistance(xCoo[i], yCoo[i], xCoo[i+1], yCoo[i+1])
    }
    //println("line length = $length")

    // initialize arrays for line segment points (and add first point)
    xCooSeg = []
    yCooSeg = []
    xCooSeg << xCoo[0] // add first point = outerMost point (0%)
    yCooSeg << yCoo[0]

    // percentage array (for the desired segment points
    // percentages = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9] // defined as a global variable
    percCountStep = 0 // counter for percentages array
    curLenght = 0 // current line length

    for (int i = 0; i < xCoo.length - 1; i++) {
        curLenght += calcPointDistance(xCoo[i], yCoo[i], xCoo[i+1], yCoo[i+1])
        // if reached curlength just more than given % of the total length
        if (curLenght > length * percentages[percCountStep]) {
            percCountStep++
            xCooSeg << xCoo[i]
            yCooSeg << yCoo[i]
        }
        if (percCountStep == percentages.size()) break
    }
    xCooSeg << xCoo[xCoo.size()-1] // add last point = innerMost point = (100%)
    yCooSeg << yCoo[xCoo.size()-1]

     // add points to the ROI manager
    rm = RoiManager.getInstance()
    curRmCount = rm.count
    for (int i = 0; i < xCooSeg.size(); i++) {
        rm.addRoi(new PointRoi(xCooSeg[i] as Double, yCooSeg[i] as Double))
        if (i == 0) rm.rename(curRmCount + i, "Point 0.0%")
        else if (i == xCooSeg.size() - 1) rm.rename(curRmCount + i, "Point 100.0%")
        else rm.rename(curRmCount + i, "Point ${percentages[i-1]*100}%")
    }

}

/**
 * reorders the lines in the RoiManager starting at the center
 * and hopefully ending at the outside.
 * FIXME: if endpoint of one line is close to an endpoint of another line
 *        one ring further out, it will connect there
 * @param imp = original ImagePlus (needed for width and height)
 * @param rm = RoiManager instance
 * @return = RoiManager instance
 */
def reorderLinesInRM(ImagePlus imp, RoiManager rm) {
    rm = RoiManager.getInstance()
    // determine the the line closest to the center point
    endPointsX = []
    endPointsY = []
    for (int i = 0; i < rm.count; i++) {
        line = rm.getRoi(i) as PolygonRoi
        endPointsX << line.getFloatPolygon().xpoints[0]
        endPointsX << line.getFloatPolygon().xpoints[line.getFloatPolygon().xpoints.length - 1]
        endPointsY << line.getFloatPolygon().ypoints[0]
        endPointsY << line.getFloatPolygon().ypoints[line.getFloatPolygon().ypoints.length - 1]
    }

    distCenter = []
    xMiddle = imp.width / 2
    yMiddle = imp.height / 2
    for (int i = 0; i < endPointsX.size(); i++) {
        distCenter << calcPointDistance(endPointsX[i], endPointsY[i], xMiddle, yMiddle)
    }

    // show the most center point on image
    index = distCenter.indexOf(distCenter.min())

    // initialize the new order of lines
    lineOrder = []
    // add the first, most middle line in the correct orientation
    if (index % 2 != 0) {
        line = rm.getRoi(Math.floor(index / 2) as Integer) as PolygonRoi
        x = reverseFloatList(line.getFloatPolygon().xpoints)
        y = reverseFloatList(line.getFloatPolygon().ypoints)
        lineOrder << new PolygonRoi(x, y, x.size(), Roi.POLYLINE)
    } else {
        lineOrder << rm.getRoi(index)
    }
    // remove the line from the manager
    rm.select(Math.floor(index / 2) as Integer)
    rm.runCommand(("Delete"))

    // get the lines in the RM as an array
    lineArrayRoiManager = []
    for (int i = 0; i < rm.count; i++) {
        lineArrayRoiManager << rm.getRoi(i)
    }

    // continue with the next lines
    for (int i = 0; i < rm.count; i++) {
        // get the endpoints of each line
        endPointsX = []
        endPointsY = []
        for (int j = 0; j < lineArrayRoiManager.size(); j++) {
            line = lineArrayRoiManager[j] as PolygonRoi
            endPointsX << line.getFloatPolygon().xpoints[0]
            endPointsX << line.getFloatPolygon().xpoints[line.getFloatPolygon().xpoints.length - 1]
            endPointsY << line.getFloatPolygon().ypoints[0]
            endPointsY << line.getFloatPolygon().ypoints[line.getFloatPolygon().ypoints.length - 1]
        }

        // calculate the distance to the previous point
        distPrevLine = []
        prevLine = lineOrder[lineOrder.size() - 1] as PolygonRoi
        xEnd = prevLine.getFloatPolygon().xpoints[prevLine.getFloatPolygon().xpoints.length - 1]
        yEnd = prevLine.getFloatPolygon().ypoints[prevLine.getFloatPolygon().ypoints.length - 1]
        for (int j = 0; j < endPointsX.size(); j++) {
            distPrevLine << calcPointDistance(endPointsX[j], endPointsY[j], xEnd, yEnd)
        }

        // identify the line and add the line to the lineOrder list
        index = distPrevLine.indexOf(distPrevLine.min())
        if (index % 2 != 0) {
            line = lineArrayRoiManager[Math.floor(index / 2) as Integer] as PolygonRoi
            x = reverseFloatList(line.getFloatPolygon().xpoints)
            y = reverseFloatList(line.getFloatPolygon().ypoints)
            lineOrder << new PolygonRoi(x, y, x.size(), Roi.POLYLINE)
        } else {
            lineOrder << lineArrayRoiManager[Math.floor(index / 2) as Integer]
        }
        println("index=$index removing at index${Math.floor(index / 2) as Integer}")
        lineArrayRoiManager.remove(Math.floor(index / 2) as Integer)

    }

    // add the new lines to the Roimanager
    rm.reset()
    for (int i = 0; i < lineOrder.size(); i++) {
        rm.addRoi(lineOrder[i])
    }
    return rm
}

/**
 * Reverses an input float[].
 * Required for inverting PolygonRoi lines
 * @param array = float[]
 * @return = reversed float[]
 */
def reverseFloatList(float[] array) {
    result = array.clone()
    countForReversing = 0
    for (int i = array.length-1; i >= 0; i--) {
        //println(array[i])
        result[countForReversing] = array[i]
        countForReversing++
    }
    return result
}

/**
 * Calculate the distance between to pixel coordinate point
 * @param x1 all floats
 * @param y1
 * @param x2
 * @param y2
 * @return distance between the two points
 */
def calcPointDistance(Float x1, Float y1, Float x2, Float y2) {
    return Math.sqrt( Math.pow((x2-x1), 2) + Math.pow((y2-y1), 2) )
}


/**
 * Count nuclei with stardist and specified model & choose output
 * @param imp = image to count nuceli
 * @param modelPath = String path to model
 * @param probT = Double probability thershold
 * @param nmsT = Double NMS threshold
 * @param output = output type as string, choice= "Label Image", "ROI Manager" or "Both"
 * @return = ROI manager instance, or label imagePlus, or if "Both" (rm, labelImp)
 */
def applyStarDistChoice(ImagePlus imp, String modelPath, Double probT, Double nmsT, String output) {
    rm = RoiManager.getInstance()
    if (rm == null) rm = new RoiManager()
    else rm.reset()
    // example: https://gist.github.com/maweigert/8dd6ef139e1cd37b2307b35fb50dee4a
    res = command.run(StarDist2D.class, false,
            "input", imp,
            "modelChoice", "Model (.zip) from File", // need this else will take standard model from stardist
            "normalizeInput", true,
            "percentileBottom", 1,
            "percentileTop", 99.8,
            "probThresh", probT, "nmsThresh", nmsT,
            "outputType", output,
            "modelFile", modelPath,
            "nTiles", 1, "excludeBoundary", 2, "verbose", true,
            "showCsbdeepProgress", true, "showProbAndDist", false
    ).get()
    if (output == "Label Image") return convertService.convert(res.getOutput("label"), ImagePlus.class)
    else if (output == "Roi Manager") return res.getOutput("roi_manager")
    else {
        rm = res.getOutput("roi_manager")
        label = convertService.convert(res.getOutput("label"), ImagePlus.class)
        return [rm, label]
    }

}

/**
 * Count nuclei with stardist and specified model & output RM instance
 * @param imp = image to count nuceli
 * @param modelPath = String path to model
 * @param probT = Double probability thershold
 * @param nmsT = Double NMS threshold
 * @return = ROI manager instance
 */
def applyStarDist(ImagePlus imp, String modelPath, Double probT, Double nmsT) {
    rm = RoiManager.getInstance()
    if (rm == null) rm = new RoiManager()
    else rm.reset()
    // example: https://gist.github.com/maweigert/8dd6ef139e1cd37b2307b35fb50dee4a
    res = command.run(StarDist2D.class, false,
            "input", imp,
            "modelChoice", "Model (.zip) from File", // need this else will take standard model from stardist
            "normalizeInput", true,
            "percentileBottom", 1,
            "percentileTop", 99.8,
            "probThresh", probT, "nmsThresh", nmsT,
            "outputType", "ROI Manager",
            "modelFile", modelPath,
            "nTiles", 1, "excludeBoundary", 2, "verbose", true,
            "showCsbdeepProgress", true, "showProbAndDist", false
    ).get()
    rm = res.getOutput("roi_manager")
    rm = RoiManager.getInstance()
    rm.runCommand(imp, "Show All")
    return rm
}


/**
 * check if a plugin is installed in Fiji
 * @param pluginName = String of the plugin name, e.g. "Binary Feature Extractor"
 * @param title = String title of error message
 * @param msg = String message of the error
 * @return true if installed, else false
 */
def checkForPlugin(String pluginName, String title, String msg) {
    // check if Binary Feature Extractor plugin is installed
    pluginList = Menus.getCommands()
    if (pluginList.get(pluginName) == null) {
        IJ.error(title, msg)
        return false
    }
    else return true
}

/**
 * function to overlay ROIs from manager on overview image
 * requires an active roimanager
 * @param imp = overview imp
 * @param rm = roi manager
 * @param color = RGB integer array
 * @param line = stroke width
 */
def markOnRGB(imp, RoiManager rm, color, line) {
    if(rm == null) return
    IJ.setForegroundColor(color[0], color[1], color[2])
    IJ.run("Line Width...", "line=" + line)
    rm.runCommand(imp, "Select All")
    rm.runCommand(imp, "Draw")
    IJ.run(imp, "Select None", "")
}

/**
 * open image with bio formats
 * will only return the first image, if multi image file
 * opens with composite display mode
 * @param file = String path
 * @return = ImagePlus object
 */
def openImageWithBF(String file) {
    options = new ImporterOptions()
    // import options
    options.setId(file)
    options.setAutoscale(true)
    //options.setSeriesOn(2, true) // 0-based
    options.setCrop(false)
    options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE)

    // open the image
    imp = BF.openImagePlus(options) // returns an array of image references
    return imp[0]
}


/**
 * Custom non-Blocking Dialog for adjusting the cell count and
 * defining category and base nuclei count
 * @param gdTitle = String dialog title
 * @return = (Base nuclei count, category choice)
 */
def checkCellCountDialog(String gdTitle) {
    String[] choices = ["Basal", "Medial", "Apical"]
    cellAD = new NonBlockingGenericDialog(gdTitle)
    cellAD.addMessage("Please adjust the nuclei recognition,\n" +
            "add ('t') or remove nuclei in the ROI manager,\n" +
            "and fill out the info below.")
    cellAD.addChoice("Category", choices, choices[0])
    cellAD.addNumericField("Number of actual inner hair cells", 20, 0)
    cellAD.hideCancelButton()
    cellAD.showDialog()
    tempNumber = cellAD.getNextNumber()
    tempChoice = cellAD.getNextChoice()
    return [tempNumber, tempChoice]
}

