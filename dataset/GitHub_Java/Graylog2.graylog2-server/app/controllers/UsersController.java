/*
 * Copyright 2013 TORCH UG
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package controllers;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import lib.*;
import lib.security.RestPermissions;
import models.PermissionsService;
import models.StreamService;
import models.User;
import models.UserService;
import models.api.requests.ChangePasswordRequest;
import models.api.requests.ChangeUserRequest;
import models.api.requests.ChangeUserRequestForm;
import models.api.requests.CreateUserRequestForm;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.data.DynamicForm;
import play.data.Form;
import play.mvc.Result;
import views.helpers.Permissions;
import views.html.system.users.edit;
import views.html.system.users.new_user;
import views.html.system.users.show;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static lib.security.RestPermissions.*;
import static views.helpers.Permissions.isPermitted;

public class UsersController extends AuthenticatedController {
    private static final Logger log = LoggerFactory.getLogger(UsersController.class);

    private static final Form<CreateUserRequestForm> createUserForm = Form.form(CreateUserRequestForm.class);
    private static final Form<ChangeUserRequestForm> changeUserForm = Form.form(ChangeUserRequestForm.class);
    private static final Form<ChangePasswordRequest> changePasswordForm = Form.form(ChangePasswordRequest.class);

    @Inject
    private UserService userService;
    @Inject
    private PermissionsService permissionsService;
    @Inject
    private StreamService streamService;

    public Result index() {
        final List<User> allUsers = isPermitted(USERS_LIST) ? userService.all() : Collections.<User>emptyList();
        final List<String> permissions = permissionsService.all();
        return ok(views.html.system.users.index.render(currentUser(), breadcrumbs(), allUsers, permissions));
    }

    public Result show(String username) {
        final User user = userService.load(username);
        if (user == null) {
            return notFound();
        }

        BreadcrumbList bc = breadcrumbs();
        bc.addCrumb(user.getFullName(), routes.UsersController.show(username));

        return ok(show.render(user, currentUser(), bc));
    }

    public Result newUserForm() {
        BreadcrumbList bc = breadcrumbs();
        bc.addCrumb("New", routes.UsersController.newUserForm());

        final List<String> permissions = permissionsService.all();
        try {
            return ok(new_user.render(
                    createUserForm,
                    currentUser(),
                    permissions,
                    ImmutableSet.<String>of(),
                    DateTools.getGroupedTimezoneIds().asMap(),
                    streamService.all(),
                    bc));
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch streams. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        }
    }

    public Result editUserForm(String username) {
        BreadcrumbList bc = breadcrumbs();
        bc.addCrumb("Edit " + username, routes.UsersController.editUserForm(username));

        User user = userService.load(username);
        final Form<ChangeUserRequestForm> form = changeUserForm.fill(new ChangeUserRequestForm(user));
        boolean requiresOldPassword = checkRequireOldPassword(username);
        try {
            return ok(edit.render(
                    form,
                    username,
                    currentUser(),
                    user,
                    requiresOldPassword,
                    permissionsService.all(),
                    ImmutableSet.copyOf(user.getPermissions()),
                    DateTools.getGroupedTimezoneIds().asMap(),
                    streamService.all(),
                    bc)
            );
        } catch (IOException e) {
            return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
        } catch (APIException e) {
            String message = "Could not fetch streams. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
            return status(504, views.html.errors.error.render(message, e, request()));
        }
    }

    public Result create() {
        Form<CreateUserRequestForm> createUserRequestForm = Tools.bindMultiValueFormFromRequest(CreateUserRequestForm.class);
        final CreateUserRequestForm request = createUserRequestForm.get();

        if (createUserRequestForm.hasErrors()) {
            BreadcrumbList bc = breadcrumbs();
            bc.addCrumb("Create new", routes.UsersController.newUserForm());
            final List<String> permissions = permissionsService.all();
            try {
                return badRequest(new_user.render(
                        createUserRequestForm,
                        currentUser(),
                        permissions,
                        ImmutableSet.copyOf(request.permissions),
                        DateTools.getGroupedTimezoneIds().asMap(),
                        streamService.all(),
                        bc));
            } catch (IOException e) {
                return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
            } catch (APIException e) {
                String message = "Could not fetch streams. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
                return status(504, views.html.errors.error.render(message, e, request()));
            }
        }
        if (request.admin) {
            request.permissions = Lists.newArrayList("*");
        } else {
            request.permissions = permissionsService.readerPermissions(request.username);
        }

        if (!userService.create(request.toApiRequest())) {
            flash("error", "Could not create user due to an internal error.");
        }
        return redirect(routes.UsersController.index());
    }

    public Result delete(String username) {
        userService.delete(username);
        return redirect(routes.UsersController.index());
    }

    public Result isUniqueUsername(String username) {
//        if (LocalAdminUser.getInstance().getName().equals(username)) {
//            return noContent();
//        }
        if (userService.load(username) == null) {
            return notFound();
        } else {
            return noContent();
        }
    }

    public Result saveChanges(String username) {
        final Form<ChangeUserRequestForm> requestForm = Form.form(ChangeUserRequestForm.class).bindFromRequest();
        final User user = userService.load(username);

        if (requestForm.hasErrors()) {
            final BreadcrumbList bc = new BreadcrumbList();
            bc.addCrumb("System", routes.SystemController.index(0));
            bc.addCrumb("Users", routes.UsersController.index());
            bc.addCrumb("Edit " + username, routes.UsersController.editUserForm(username));

            final List<String> all = permissionsService.all();
            boolean requiresOldPassword = checkRequireOldPassword(username);

            try {
                return badRequest(edit.render(
                        requestForm,
                        username,
                        currentUser(),
                        user,
                        requiresOldPassword,
                        all,
                        ImmutableSet.copyOf(requestForm.get().permissions),
                        DateTools.getGroupedTimezoneIds().asMap(),
                        streamService.all(),
                        bc));
            } catch (IOException e) {
                return status(504, views.html.errors.error.render(ApiClient.ERROR_MSG_IO, e, request()));
            } catch (APIException e) {
                String message = "Could not fetch streams. We expected HTTP 200, but got a HTTP " + e.getHttpCode() + ".";
                return status(504, views.html.errors.error.render(message, e, request()));
            }
        }

        final ChangeUserRequestForm formData = requestForm.get();
        Set<String> permissions = Sets.newHashSet(user.getPermissions());
        // TODO this does not handle combined permissions like streams:edit,read:1,2 !
        // remove all streams:edit, streams:read permissions and add the ones from the form back.

        permissions = Sets.newHashSet(Sets.filter(permissions, new Predicate<String>() {
            @Override
            public boolean apply(@Nullable String input) {
                return (input != null) &&
                        !(input.startsWith(STREAMS_READ) || input.startsWith(STREAMS_EDIT));
            }
        }));
        for (String streampermission : formData.streampermissions) {
            permissions.add(RestPermissions.STREAMS_READ + ":" + streampermission);
        }
        for (String streameditpermission : formData.streameditpermissions) {
            permissions.add(RestPermissions.STREAMS_EDIT + ":" + streameditpermission);
        }
        final ChangeUserRequest changeRequest = formData.toApiRequest();
        changeRequest.permissions = Lists.newArrayList(permissions);
        user.update(changeRequest);

        return redirect(routes.UsersController.index());
    }

    private boolean checkRequireOldPassword(String username) {
        boolean requiresOldPassword = true;
        final User currentUser = currentUser();
        final Subject subject = currentUser.getSubject();
        final String currentUserName = currentUser.getName();
        if (subject.isPermitted("users:passwordchange:*")) {
            // if own account, require old password, otherwise don't require it
            requiresOldPassword = currentUserName.equals(username);
        }
        return requiresOldPassword;
    }

    public Result changePassword(String username) {
        final Form<ChangePasswordRequest> requestForm = changePasswordForm.bindFromRequest("old_password", "password");

        final ChangePasswordRequest request = requestForm.get();
        final User user = userService.load(username);

        if (checkRequireOldPassword(username) && request.old_password == null) {
            requestForm.reject("Old password is required.");
        }
        if (requestForm.hasErrors() || !user.updatePassword(request)) {
            flash("error", "Could not update the password.");
            return redirect(routes.UsersController.editUserForm(username));
        }

        flash("success", "Successfully changed the password for user " + user.getFullName());
        return redirect(routes.UsersController.index());
    }

    public Result resetPermissions(String username) {
        final DynamicForm requestForm = Form.form().bindFromRequest();

        boolean isAdmin = false;
        final String field = requestForm.get("admin");
        if (field != null && field.equalsIgnoreCase("on")) {
            isAdmin = true;
        }
        final User user = userService.load(username);

        if (!Permissions.isPermitted(USERS_PERMISSIONSEDIT) || user.isReadonly()) {
            flash("error", "Unable to reset permissions!");
            return redirect(routes.UsersController.index());
        }

        final ChangeUserRequest changeRequest = new ChangeUserRequest(user);
        if (isAdmin) {
            changeRequest.permissions = Lists.newArrayList("*");
        } else {
            changeRequest.permissions = permissionsService.readerPermissions(username);
        }
        final boolean success = user.update(changeRequest);
        if (success) {
            flash("success", "Successfully reset permission for " + user.getFullName() + " to " + (isAdmin ? "administrator" : "reader") + " permissions.");
        } else {
            flash("error", "Unable to reset permissions for user " + user.getFullName());
        }
        return redirect(routes.UsersController.index());
    }

    private static BreadcrumbList breadcrumbs() {
        BreadcrumbList bc = new BreadcrumbList();
        bc.addCrumb("System", routes.SystemController.index(0));
        bc.addCrumb("Users", routes.UsersController.index());
        return bc;
    }
}
