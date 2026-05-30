package com.spx.express.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.storage.SessionManager
import com.spx.express.ui.admin.AdminDashboardActivity
import com.spx.express.ui.branch_manager.BranchManagerDashboardActivity
import com.spx.express.ui.customer.CustomerDashboardActivity
import com.spx.express.ui.rider.RiderDashboardActivity
import com.spx.express.ui.staff.StaffDashboardActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSignIn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Check if user is already logged in, redirect directly to dashboard
        if (sessionManager.isLoggedIn()) {
            redirectBasedOnRole(sessionManager.getUserRole() ?: "")
            finish()
            return
        }

        // Initialize UI components
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)

        btnSignIn.setOnClickListener {
            handleSignIn()
        }

        findViewById<TextView>(R.id.tvSignUpLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun handleSignIn() {
        val email = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""

        if (email.isEmpty()) {
            etEmail.error = "Email address is required"
            return
        }
        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            return
        }

        btnSignIn.isEnabled = false
        btnSignIn.text = "Signing In..."

        lifecycleScope.launch {
            try {
                // Escape email string for Firebase REST equalTo query
                // 1. First, search inside the Staff nodes
                val staffResponse = RetrofitClient.instance.getAllStaff()
                if (staffResponse.isSuccessful) {
                    val staffMap = staffResponse.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val staff = staffMap.values.find { it?.stfEmail?.equals(email, ignoreCase = true) == true }
                        if (staff != null) {
                            // Verify hashed credentials with BCrypt (normalize $2y$ to $2a$ for JVM compatibility)
                            val dbHash = staff.stfPasswordHash
                            val cleanHash = if (dbHash.startsWith("$2y$")) dbHash.replaceFirst("$2y$", "$2a$") else dbHash
                            val isPasswordCorrect = try {
                                org.mindrot.jbcrypt.BCrypt.checkpw(password, cleanHash)
                            } catch (e: Exception) {
                                false
                            }

                            if (!isPasswordCorrect) {
                                Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_LONG).show()
                                btnSignIn.isEnabled = true
                                btnSignIn.text = "Sign In"
                                return@launch
                            }

                            sessionManager.createLoginSession(
                                userId = staff.stfId,
                                email = staff.stfEmail,
                                role = staff.stfRole,
                                displayName = "${staff.stfFname} ${staff.stfLname}"
                            )
                            Toast.makeText(this@LoginActivity, "Welcome back, ${staff.stfFname}!", Toast.LENGTH_SHORT).show()
                            redirectBasedOnRole(staff.stfRole)
                            finish()
                            return@launch
                        }
                    }
                }

                // 2. If not found in Staff, search inside the Customer nodes
                val customerResponse = RetrofitClient.instance.getAllCustomers()
                if (customerResponse.isSuccessful) {
                    val customerMap = customerResponse.body()
                    if (!customerMap.isNullOrEmpty()) {
                        val customer = customerMap.values.find { it?.custEmail?.equals(email, ignoreCase = true) == true }
                        if (customer != null) {
                            // Verify hashed credentials with BCrypt (normalize $2y$ to $2a$ for JVM compatibility)
                            val dbHash = customer.custPasswordHash
                            val cleanHash = if (dbHash.startsWith("$2y$")) dbHash.replaceFirst("$2y$", "$2a$") else dbHash
                            val isPasswordCorrect = try {
                                org.mindrot.jbcrypt.BCrypt.checkpw(password, cleanHash)
                            } catch (e: Exception) {
                                false
                            }

                            if (!isPasswordCorrect) {
                                Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_LONG).show()
                                btnSignIn.isEnabled = true
                                btnSignIn.text = "Sign In"
                                return@launch
                            }

                            sessionManager.createLoginSession(
                                userId = customer.custId,
                                email = customer.custEmail,
                                role = "customer",
                                displayName = "${customer.custFname} ${customer.custLname}"
                            )
                            Toast.makeText(this@LoginActivity, "Welcome back, ${customer.custFname}!", Toast.LENGTH_SHORT).show()
                            redirectBasedOnRole("customer")
                            finish()
                            return@launch
                        }
                    }
                }

                // Fallback: If not found in either node
                Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSignIn.isEnabled = true
                btnSignIn.text = "Sign In"
            }
        }
    }

    private fun redirectBasedOnRole(role: String) {
        val intent = when (role.lowercase()) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "branch_manager" -> Intent(this, BranchManagerDashboardActivity::class.java)
            "staff" -> Intent(this, StaffDashboardActivity::class.java)
            "rider" -> Intent(this, RiderDashboardActivity::class.java)
            else -> Intent(this, CustomerDashboardActivity::class.java)
        }
        startActivity(intent)
    }
}
