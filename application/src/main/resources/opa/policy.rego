package s1000d.authz

default allow := false

is_view_action {
  startswith(input.action, "VIEW_")
}

has_role(role) {
  some i
  input.user.roles[i] == role
}

allow {
  is_view_action
  has_view_role
}

allow {
  input.action == "UPLOAD_MODULE"
  has_upload_role
}

allow {
  input.action == "REINDEX"
  has_role("ROLE_ADMIN")
}

allow {
  input.action == "MANAGE_USERS"
  has_role("ROLE_ADMIN")
}

has_view_role {
  has_role("ROLE_VIEWER")
}

has_view_role {
  has_role("ROLE_ENGINEER")
}

has_view_role {
  has_role("ROLE_ADMIN")
}

has_upload_role {
  has_role("ROLE_ENGINEER")
}

has_upload_role {
  has_role("ROLE_ADMIN")
}
