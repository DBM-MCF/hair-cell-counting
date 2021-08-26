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

## Usage

Download [Fiji](https://fiji.sc)
Enable following updates sites:
* StarDist
* CSBDeep
* Biomedgroup

When using our StarDist model, set the TensorFlow settings in Fiji to version 1.14.0.
This is the TensorFlow version the model was trained on.

Please check the supplemental material of our article for a detailed [step-by-step guide](https://ars.els-cdn.com/content/image/1-s2.0-S0378595521001519-mmc2.zip) of our scripts.

## How to cite

Cortada, M., Sauteur, L., Lanz, M., Levano, S., & Bodmer, D. (2021). 
A deep learning approach to quantify auditory hair cells. 
Hearing Research, 409, 108317. 
[http://doi.org/10.1016/j.heares.2021.108317](http://doi.org/10.1016/j.heares.2021.108317)

And the [StarDist plugins as described here](https://github.com/stardist/stardist#how-to-cite).
