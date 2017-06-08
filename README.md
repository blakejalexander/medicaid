# medicaid
[CSSE4011 2017](https://www.uq.edu.au/study/course.html?course_code=CSSE4011) Project
An Android based fall detection and medical alert system. This is a end-of-semester project
and the result of 3-4 weeks of scoping, research and implementation which was presented
at the conclusion of the semester.

This repository consists of two components. An Android application which passively monitors
the device to detect the user falling. If the user does not acknowledge that they are okay,
the application will contact any registered guardians to alert them with the location of the
user.

The second component, is a trained neural network implemented using the TensorFlow 
DNNClassifier class from tf.contrib.learn. A TCP server written in Python is used to answer
classification requests, classifying "Fall-like" events as either a fall, jump, walk or an
(accidental) hit to the sensor. The fall-like events are detected by the Android application.

We achieved a model accuracy of 70% from a training set of 86 and a verification set of 39.
The training features can be found under the machine_learning/ path.

At time of writing, this repository targets Android with a minimum API version of 21. The
devices developed upon were a Nexus 6P (Monitored User) and a Lenovo Tab (Guardian User).
The machine learning component is built against TensorFlow 1.1.0.
