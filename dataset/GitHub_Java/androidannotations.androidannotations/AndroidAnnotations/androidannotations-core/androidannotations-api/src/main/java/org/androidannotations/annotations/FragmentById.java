/**
 * Copyright (C) 2010-2015 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Use it on android.app.Fragment or android.support.v4.app.Fragment fields in
 * activity classes to retrieve and inject a fragment.
 * </p>
 * <p>
 * The annotation value should be one of R.id.* fields. If not set, the field
 * name will be used as the R.id.* field name.
 * </p>
 * <p>
 * <b>Note:</b> This can only inject an existing fragment, not create them.
 * </p>
 * <blockquote>
 * 
 * Example :
 * 
 * <pre>
 * &lt;LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" &gt;
 * 
 *     &lt;fragment
 *         android:id="@+id/<b>myFragment</b>"
 *         android:name="mypackage.MyFragment_"
 *         android:layout_width="match_parent"
 *         android:layout_height="match_parent" /&gt;
 * &lt;/LinearLayout&gt;
 * 
 * 
 * &#064;EActivity(R.layout.main)
 * public class MyActivity extends Activity {
 * 
 * // all injected fragment will be the same
 * 
 * 	&#064;FragmentById
 * 	public MyFragment <b>myFragment</b>;
 * 	
 * 	&#064;FragmentById(R.id.<b>myFragment</b>)
 * 	public MyFragment myFragment2;
 * }
 * </pre>
 * 
 * </blockquote>
 * 
 * <p>
 * To use the <code>getChildFragmentManager()</code> to inject the
 * <code>Fragment</code>, set the {@link #childFragment()} annotation parameter
 * to <code>true</code>. You can only do this if the annotated field is in a
 * class which extends <code>android.app.Fragment</code> or
 * <code>android.support.v4.app.Fragment</code> and the
 * <code>getChildFragmentManager()</code> method is available.
 * </p>
 * 
 * <blockquote>
 * 
 * Example :
 * 
 * <pre>
 * &lt;LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" &gt;
 * 
 *     &lt;fragment
 *         android:id="@+id/myChildFragment"
 *         android:name="mypackage.MyChildFragment_"
 *         android:layout_width="match_parent"
 *         android:layout_height="match_parent" /&gt;
 * &lt;/LinearLayout&gt;
 * 
 * 
 * &#064;EFragment(R.layout.parentfragment)
 * public class MyParentFragment extends Fragment {
 * 
 * 	&#064;FragmentById(<b>childFragment = true</b>)
 * 	MyChildFragment myFragment;
 * 
 * }
 * </pre>
 * 
 * </blockquote>
 * 
 * @see EFragment
 * @see FragmentArg
 * @see FragmentByTag
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface FragmentById {

	/**
	 * The R.id.* field which is the id of the Fragment.
	 * 
	 * @return the id of the Fragment
	 */
	int value() default ResId.DEFAULT_VALUE;

	/**
	 * The resource name which refers to the id of the Fragment.
	 * 
	 * @return the resource name of the Fragment
	 */
	String resName() default "";

	/**
	 * Whether to use <code>getChildFragmentManager()</code> or
	 * <code>getFragmentManager()</code> to obtain the Fragment. Only can be
	 * <code>true</code> when injecting into a <code>Fragment</code>.
	 * 
	 * @return <code>true</code> to use <code>getChildFragmentManager()</code>,
	 *         <code>false</code> to use <code>getFragmentManager()</code>
	 */
	boolean childFragment() default false;
}
