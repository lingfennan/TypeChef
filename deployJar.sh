rm Frontend/target/*.jar
sbt assembly
cp Frontend/target/*.jar deploy
git checkout gh-pages
git add deploy/*.jar
git commit -m "deployed new .jar file"
git checkout master
