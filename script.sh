rpm -ivh https://packages.chef.io/stable/el/7/chefdk-0.19.6-1.el7.x86_64.rpm

yum install git -y

git clone https://github.com/fabiogoma/NuRecipes.git

chef-client -z NuRecipes/aws_worker/recipes/docker_install.rb
