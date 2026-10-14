# Run inside this local GitLab with gitlab-rails runner.
require 'json'
root = User.find_by!(username: 'root')
ApplicationSetting.current.update!(signup_enabled: false)
project = Project.find_by_full_path('root/Carlauncher')
unless project
  project = Projects::CreateService.new(root, {
    name: 'Carlauncher', path: 'Carlauncher', namespace_id: root.namespace_id,
    visibility_level: Gitlab::VisibilityLevel::PRIVATE,
    initialize_with_readme: false
  }).execute
  raise project.errors.full_messages.join(', ') unless project.persisted?
end
project.update!(build_timeout: 7200)
pub = File.read('/tmp/carlauncher-key.pub').strip
unless root.keys.exists?(key: pub)
  key = root.keys.create!(title: 'Carlauncher local workstation', key: pub, organization_id: project.organization_id)
end
runner = Ci::Runner.find_by(description: 'Carlauncher local Docker')
unless runner
  runner = Ci::Runner.create!(
    runner_type: :project_type, description: 'Carlauncher local Docker',
    projects: [project], organization_id: project.organization_id, tag_list: ['carlauncher-docker'],
    run_untagged: false, locked: true, access_level: :not_protected
  )
end
token = root.personal_access_tokens.create!(name: 'Local GitLab setup', scopes: ['api'], expires_at: 7.days.from_now)
File.write('/tmp/carlauncher-bootstrap.json', JSON.generate({
  project_id: project.id, project_url: project.web_url,
  runner_token: runner.token, api_token: token.token
}))
File.chmod(0600, '/tmp/carlauncher-bootstrap.json')
puts "Project #{project.id} and runner #{runner.id} prepared"
