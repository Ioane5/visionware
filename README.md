## VisionWare
Hackathon HackUPC project

This is unique application that intends to help people with vision impedance to find items in the room. 

### Idea
Idea is that user should be able to find exact location of the requested item from Voice assistant. 
User should also get brief feedback of what items are nearby. 

The phone/tablet scans the room while user holds device. Ideally I think users will have attached device on chest for full experience.

User can ask for specific item:

**User:**           _"Please find me my laptop"_  
**Assistant**    _"I found one laptop nearby, it's in 2 meters left from you."_  

if there are multiple items in the room answer will be following  
**Assistant**     _"I found five laptops nearby, the nearest one is in 2 meters left from you."_

User can ask for nearby items:

**User:**           _"What items are nearby?"_  
**Assistant**     _"There are 5 laptops, 3 chairs, 2 tables and 10 more different items near you."_

### How it's done?
I used new [Google Project-Tango](https://get.google.com/tango/) that enables device to scan the room and localize later in the same room to the exact coordinate system. 
I also used [clarifai](https://www.clarifai.com/) API for labeling items in the room. 
The mixture of those apis (and couple of more Android libraries) is VisionWare :)
