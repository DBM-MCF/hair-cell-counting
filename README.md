Fiji scripts for semi-automatic hair cell counting
======

Two scripts for hair cell counting in Fiji using [StarDist](https://imagej.net/StarDist) in 2D images.

## Supplemental information
StarDist2D model and training data set can be found on Zenodo [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.4590066.svg)](https://doi.org/10.5281/zenodo.4590066)

## Important disclaimer
The Myers laboratory created StarDist.
StarDist original code and documentation are available on [GitHub](https://github.com/stardist/stardist). 

We used these resources to create a custem StarDist model.
If you use these scripts, **please also cite the following original papers**:
* [Cell Detection with Star-convex Polygons.](https://arxiv.org/abs/1806.03535)
* [Star-convex Polyhedra for 3D Object Detection and Segmentation in Microscopy.](http://openaccess.thecvf.com/content_WACV_2020/papers/Weigert_Star-convex_Polyhedra_for_3D_Object_Detection_and_Segmentation_in_Microscopy_WACV_2020_paper.pdf)

---

## Usage

### Requirements
1. Download [Fiji](https://fiji.sc).
2. Enable following updates sites:
    * StarDist
    * CSBDeep
    * Biomedgroup
3. Get the our StarDist model [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.4590066.svg)](https://doi.org/10.5281/zenodo.4590066)
    *  When using our StarDist model, set the TensorFlow settings in Fiji to version 1.14.0. This is the TensorFlow version the model was trained on.
4. Download our scripts

### Step-by-step guide
Please check the supplemental material of our article for a detailed [step-by-step guide](https://ars.els-cdn.com/content/image/1-s2.0-S0378595521001519-mmc2.zip) of our scripts.

---

## How to cite
Us:
* Cortada, M., Sauteur, L., Lanz, M., Levano, S., & Bodmer, D. (2021). 
  A deep learning approach to quantify auditory hair cells. 
  Hearing Research, 409, 108317. [http://doi.org/10.1016/j.heares.2021.108317](http://doi.org/10.1016/j.heares.2021.108317)

StarDist plugin as described [here](https://github.com/stardist/stardist#how-to-cite):

* Schmidt, U., Weigert, M., Broaddus, C., Myers, G. (2018). Cell Detection with  Star-Convex Polygons. In: Frangi, A., Schnabel, J., Davatzikos, C., Alberola-López, C., Fichtinger, G. (eds) Medical Image Computing and Computer Assisted Intervention – MICCAI 2018. MICCAI 2018. Lecture Notes in Computer Science(), vol 11071. Springer, Cham. 
[https://doi.org/10.1007/978-3-030-00934-2_30](https://doi.org/10.1007/978-3-030-00934-2_30)
* M. Weigert, U. Schmidt, R. Haase, K. Sugawara and G. Myers, "Star-convex Polyhedra for 3D Object Detection and Segmentation in Microscopy," 2020 IEEE Winter Conference on Applications of Computer Vision (WACV), 2020, pp. 3655-3662, [doi: 10.1109/WACV45572.2020.9093435.](https://doi.org/10.1109/WACV45572.2020.9093435)

Biomedgroup:
* Glaser, M., Schnauß, J., Tschirner, T., Schmidt, S., Moebius-Winkler, M., Käs, J. A., & Smith, D. M. (2016). Self-assembly of hierarchically ordered structures in DNA nanotube systems. New Journal of Physics New J. Phys., 18(5), 055001. [doi:10.1088/1367-2630/18/5/055001](https://doi.org/10.1088/1367-2630/18/5/055001)

Fiji:
* Schindelin, J., Arganda-Carreras, I., Frise, E., Kaynig, V., Longair, M., Pietzsch, T., et al. (2012). Fiji: an open-source platform for biological-image analysis. Nature Methods, 9(7), 676–682. [http://doi.org/10.1038/nmeth.2019](http://doi.org/10.1038/nmeth.2019)